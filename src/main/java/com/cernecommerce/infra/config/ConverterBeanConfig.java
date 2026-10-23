package com.cernecommerce.infra.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cernecommerce.adapter.in.converter.CampaignDTOConverter;
import com.cernecommerce.adapter.in.converter.CashbackDTOConverter;
import com.cernecommerce.adapter.in.converter.ChannelStatusDTOConverter;
import com.cernecommerce.adapter.in.converter.CustomerCsvConverter;
import com.cernecommerce.adapter.in.converter.CustomerDTOConverter;
import com.cernecommerce.adapter.in.converter.CustomerNoteDTOConverter;
import com.cernecommerce.adapter.in.converter.GoodsReceiptDTOConverter;
import com.cernecommerce.adapter.in.converter.NfeImportDTOConverter;
import com.cernecommerce.adapter.in.converter.PermissionDTOConverter;
import com.cernecommerce.adapter.in.converter.KitBuilderDTOConverter;
import com.cernecommerce.adapter.in.converter.ShopCartDTOConverter;
import com.cernecommerce.adapter.in.converter.ShopCatalogDTOConverter;
import com.cernecommerce.adapter.in.converter.StageTransitionDTOConverter;
import com.cernecommerce.adapter.in.converter.TagDTOConverter;
import com.cernecommerce.adapter.in.converter.BrandDTOConverter;
import com.cernecommerce.adapter.in.converter.CategoryDTOConverter;
import com.cernecommerce.adapter.in.converter.ProductDTOConverter;
import com.cernecommerce.adapter.in.converter.ReplenishmentListDTOConverter;
import com.cernecommerce.adapter.in.converter.RoleDTOConverter;
import com.cernecommerce.adapter.in.converter.CashRegisterDTOConverter;
import com.cernecommerce.adapter.in.converter.ComandaDTOConverter;
import com.cernecommerce.adapter.in.converter.OrderDTOConverter;
import com.cernecommerce.adapter.in.converter.PedidoAppDTOConverter;
import com.cernecommerce.adapter.in.converter.StockCountDTOConverter;
import com.cernecommerce.adapter.in.converter.StockReservationDTOConverter;
import com.cernecommerce.adapter.in.converter.StockMovementDTOConverter;
import com.cernecommerce.adapter.in.converter.UserDTOConverter;
import com.cernecommerce.adapter.in.converter.WarehouseDTOConverter;
import com.cernecommerce.adapter.out.persistence.converter.UserEntityConverter;

@Configuration
class ConverterBeanConfig {

    @Bean
    UserEntityConverter userEntityConverter() {
        return new UserEntityConverter();
    }

    @Bean
    UserDTOConverter userDTOConverter(@Value("${avatar.base-url:http://localhost:8080}") String avatarBaseUrl) {
        return new UserDTOConverter(avatarBaseUrl);
    }

    @Bean
    RoleDTOConverter roleDTOConverter() {
        return new RoleDTOConverter();
    }

    @Bean
    PermissionDTOConverter permissionDTOConverter() {
        return new PermissionDTOConverter();
    }

    @Bean
    ProductDTOConverter productDTOConverter() {
        return new ProductDTOConverter();
    }

    @Bean
    CategoryDTOConverter categoryDTOConverter() {
        return new CategoryDTOConverter();
    }

    @Bean
    BrandDTOConverter brandDTOConverter() {
        return new BrandDTOConverter();
    }

    @Bean
    ReplenishmentListDTOConverter replenishmentListDTOConverter() {
        return new ReplenishmentListDTOConverter();
    }

    @Bean
    WarehouseDTOConverter warehouseDTOConverter() {
        return new WarehouseDTOConverter();
    }

    @Bean
    StockCountDTOConverter stockCountDTOConverter() {
        return new StockCountDTOConverter();
    }

    @Bean
    StockReservationDTOConverter stockReservationDTOConverter() {
        return new StockReservationDTOConverter();
    }

    @Bean
    StockMovementDTOConverter stockMovementDTOConverter() {
        return new StockMovementDTOConverter();
    }

    @Bean
    GoodsReceiptDTOConverter goodsReceiptDTOConverter() {
        return new GoodsReceiptDTOConverter();
    }

    @Bean
    NfeImportDTOConverter nfeImportDTOConverter() {
        return new NfeImportDTOConverter();
    }

    @Bean
    CustomerDTOConverter customerDTOConverter() {
        return new CustomerDTOConverter();
    }

    @Bean
    CustomerNoteDTOConverter customerNoteDTOConverter() {
        return new CustomerNoteDTOConverter();
    }

    @Bean
    StageTransitionDTOConverter stageTransitionDTOConverter() {
        return new StageTransitionDTOConverter();
    }

    @Bean
    TagDTOConverter tagDTOConverter() {
        return new TagDTOConverter();
    }

    @Bean
    CustomerCsvConverter customerCsvConverter() {
        return new CustomerCsvConverter();
    }

    @Bean
    CampaignDTOConverter campaignDTOConverter() {
        return new CampaignDTOConverter();
    }

    @Bean
    ChannelStatusDTOConverter channelStatusDTOConverter() {
        return new ChannelStatusDTOConverter();
    }

    @Bean
    OrderDTOConverter orderDTOConverter() {
        return new OrderDTOConverter();
    }

    @Bean
    PedidoAppDTOConverter pedidoAppDTOConverter(OrderDTOConverter orderDTOConverter) {
        return new PedidoAppDTOConverter(orderDTOConverter);
    }

    @Bean
    CashRegisterDTOConverter cashRegisterDTOConverter() {
        return new CashRegisterDTOConverter();
    }

    @Bean
    ComandaDTOConverter comandaDTOConverter() {
        return new ComandaDTOConverter();
    }

    @Bean
    CashbackDTOConverter cashbackDTOConverter() {
        return new CashbackDTOConverter();
    }

    @Bean
    ShopCatalogDTOConverter shopCatalogDTOConverter() {
        return new ShopCatalogDTOConverter();
    }

    @Bean
    ShopCartDTOConverter shopCartDTOConverter() {
        return new ShopCartDTOConverter();
    }

    @Bean
    KitBuilderDTOConverter kitBuilderDTOConverter() {
        return new KitBuilderDTOConverter();
    }
}
