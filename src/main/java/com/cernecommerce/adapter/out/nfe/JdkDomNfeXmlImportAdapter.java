package com.cernecommerce.adapter.out.nfe;

import com.cernecommerce.core.domain.exception.compras.MalformedNfeXmlException;
import com.cernecommerce.core.domain.exception.estoque.BarcodeNotFoundException;
import com.cernecommerce.core.domain.model.compras.NfeImportLine;
import com.cernecommerce.core.domain.model.compras.NfeImportPreview;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.out.estoque.NfeXmlImportPort;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser de NF-e em JDK puro — {@code DocumentBuilderFactory}/DOM, sem dependência nova (EST-F005).
 * O projeto não tinha nenhuma lib de XML antes desta classe, e só ~6 campos planos por item
 * precisam ser extraídos, sem validação de schema completo (a XSD da SEFAZ é grande e versionada;
 * não vale a pena para o que este import precisa fazer).
 *
 * <h2>Hardening contra XXE — requisito de segurança, não opcional</h2>
 * <p>O XML vem de upload de um usuário autenticado, mas ainda assim de conteúdo não confiável
 * (pode ser a NF-e errada, ou uma tentativa deliberada de XXE). DOCTYPE e entidades externas são
 * desabilitados antes de qualquer parsing — ver {@link #hardenedFactory()}.</p>
 *
 * <h2>Namespace-agnóstico, de propósito</h2>
 * <p>NF-e real usa o namespace {@code http://www.portalfiscal.inf.br/nfe}, mas o parser não é
 * namespace-aware: {@code getElementsByTagName} casa pelo nome local do elemento independente do
 * namespace declarado. Evita a complexidade de XPath com namespace só para seis tags planas.</p>
 */
public class JdkDomNfeXmlImportAdapter implements NfeXmlImportPort {

    private static final String SEM_GTIN = "SEM GTIN";

    private final EstoqueUseCase estoqueUseCase;

    public JdkDomNfeXmlImportAdapter(EstoqueUseCase estoqueUseCase) {
        this.estoqueUseCase = estoqueUseCase;
    }

    @Override
    public NfeImportPreview parse(byte[] xmlBytes) {
        Document document = parseDocument(xmlBytes);
        try {
            String emitterCnpj = requiredText(firstElement(document.getElementsByTagName("emit")), "CNPJ");

            NodeList detNodes = document.getElementsByTagName("det");
            if (detNodes.getLength() == 0) {
                throw new MalformedNfeXmlException("NF-e sem nenhum item (<det>)");
            }

            List<NfeImportLine> lines = new ArrayList<>(detNodes.getLength());
            for (int i = 0; i < detNodes.getLength(); i++) {
                lines.add(parseLine((Element) detNodes.item(i)));
            }
            return new NfeImportPreview(emitterCnpj, lines, accessKey(document));
        } catch (MalformedNfeXmlException e) {
            throw e;
        } catch (Exception e) {
            // Qualquer outra falha ao caminhar pelo DOM (tag ausente, número/data inválidos) é o
            // mesmo problema do ponto de vista de quem chama: o XML não tem a estrutura esperada.
            // EST-C021 — a mensagem do erro original (tipicamente NumberFormatException, em inglês)
            // fica na causa, não no corpo da resposta.
            throw MalformedNfeXmlException.fromParser(e);
        }
    }

    private NfeImportLine parseLine(Element det) {
        int itemNumber;
        try {
            itemNumber = Integer.parseInt(det.getAttribute("nItem"));
        } catch (NumberFormatException e) {
            throw new MalformedNfeXmlException("atributo nItem ausente ou inválido em <det>");
        }

        Element prod = firstElement(det.getElementsByTagName("prod"));
        if (prod == null) {
            throw new MalformedNfeXmlException("item " + itemNumber + " sem <prod>");
        }

        String supplierProductCode = requiredText(prod, "cProd");
        String description = optionalText(prod, "xProd");
        BigDecimal quantity = new BigDecimal(requiredText(prod, "qCom"));
        BigDecimal unitPrice = new BigDecimal(requiredText(prod, "vUnCom"));

        String rawEan = optionalText(prod, "cEAN");
        String ean = (rawEan == null || rawEan.isBlank() || SEM_GTIN.equalsIgnoreCase(rawEan.trim()))
                ? null : rawEan.trim();

        Element rastro = firstElement(prod.getElementsByTagName("rastro"));
        String lotCode = rastro == null ? null : optionalText(rastro, "nLote");
        String rawExpiry = rastro == null ? null : optionalText(rastro, "dVal");
        LocalDate expiryDate = (rawExpiry == null || rawExpiry.isBlank()) ? null : LocalDate.parse(rawExpiry);

        String matchedSku = matchByEan(ean);

        // V182 — unidade comercial (a da quantidade qCom) e tributável. Informativas: o fator que leva
        // "CX" a "UN" vem do operador (ou do que ele já ensinou para este fornecedor + cProd).
        String unit = blankToNull(optionalText(prod, "uCom"));
        String taxUnit = blankToNull(optionalText(prod, "uTrib"));
        String rawTaxQuantity = blankToNull(optionalText(prod, "qTrib"));
        BigDecimal taxQuantity = rawTaxQuantity == null ? null : new BigDecimal(rawTaxQuantity);

        return NfeImportLine.fromXml(itemNumber, supplierProductCode, ean, description, quantity, unitPrice,
                lotCode, expiryDate, matchedSku, unit, taxUnit, taxQuantity);
    }

    /**
     * V182 — chave de acesso, 44 dígitos. Vem do protocolo de autorização ({@code protNFe/infProt/chNFe})
     * quando o XML é o "procNFe" autorizado, e senão do atributo {@code Id="NFe…"} de {@code infNFe}.
     * Nula quando nenhum dos dois tem 44 dígitos: a nota ainda é importável, só sem a trava de repetida.
     */
    static String accessKey(Document document) {
        Element infProt = firstElement(document.getElementsByTagName("infProt"));
        String fromProtocol = infProt == null ? null : digitsOnly(optionalText(infProt, "chNFe"));
        if (fromProtocol != null && fromProtocol.length() == 44) {
            return fromProtocol;
        }
        Element infNFe = firstElement(document.getElementsByTagName("infNFe"));
        String fromId = infNFe == null ? null : digitsOnly(infNFe.getAttribute("Id"));
        return fromId != null && fromId.length() == 44 ? fromId : null;
    }

    private static String digitsOnly(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** {@code null} quando não há EAN, ou quando nenhum produto do catálogo tem esse código de barras. */
    private String matchByEan(String ean) {
        if (ean == null) {
            return null;
        }
        try {
            return estoqueUseCase.findProductByBarcode(ean).sku();
        } catch (BarcodeNotFoundException e) {
            return null;
        }
    }

    private Document parseDocument(byte[] xmlBytes) {
        try {
            DocumentBuilder builder = hardenedFactory().newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(xmlBytes));
            document.getDocumentElement().normalize();
            return document;
        } catch (SAXException | IOException | ParserConfigurationException e) {
            // Mensagem deliberadamente genérica: não confirma a um eventual atacante se o XML foi
            // rejeitado por ser malformado ou por conter uma tentativa de DOCTYPE/entidade externa
            // — os dois casos caem aqui. EST-C021: antes o comentário dizia isso e o código fazia o
            // contrário, porque e.getMessage() do Xerces nomeia o DOCTYPE bloqueado.
            throw MalformedNfeXmlException.fromParser(e);
        }
    }

    /**
     * DOCTYPE e entidades externas desabilitados — sem isso, um XML com
     * {@code <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>} conseguiria ler arquivo
     * local ou fazer SSRF através do parser. Este é o requisito de segurança real desta classe.
     */
    private static DocumentBuilderFactory hardenedFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Parser XML do runtime não suporta o hardening exigido", e);
        }
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(false);
        return factory;
    }

    private static Element firstElement(NodeList nodes) {
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    private static String optionalText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        Node node = nodes.item(0);
        return node.getTextContent() == null ? null : node.getTextContent().trim();
    }

    private static String requiredText(Element parent, String tagName) {
        if (parent == null) {
            throw new MalformedNfeXmlException("elemento ausente para ler <" + tagName + ">");
        }
        String text = optionalText(parent, tagName);
        if (text == null || text.isBlank()) {
            throw new MalformedNfeXmlException("<" + tagName + "> ausente ou vazio");
        }
        return text;
    }
}
