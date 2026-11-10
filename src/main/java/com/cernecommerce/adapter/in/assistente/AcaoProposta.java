package com.cernecommerce.adapter.in.assistente;

import com.cernecommerce.adapter.in.dtos.response.AssistenteCampoDTO;

import java.util.List;

/**
 * Escrita que uma ferramenta propõe. {@code parametrosJson} é o que o executor do tipo vai ler no
 * confirmar; {@code campos} é o resumo do cartão, montado dos mesmos parâmetros.
 */
public record AcaoProposta(String tipo, String parametrosJson, String titulo, List<AssistenteCampoDTO> campos) { }
