package vip.xiaonuo.core.config;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/** Adds the SRM group without replacing Spring's profile-bound list of upstream groups. */
@Configuration
public class SrmOpenApiConfiguration {
    @Bean
    public GroupedOpenApi srmOpenApi() {
        return GroupedOpenApi.builder().group("SRM").packagesToScan("vip.xiaonuo.srm").build();
    }
}
