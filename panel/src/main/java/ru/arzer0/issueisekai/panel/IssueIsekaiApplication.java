package ru.arzer0.issueisekai.panel;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.arzer0.issueisekai.panel.server.ResourcePackService;

@SpringBootApplication
@EnableScheduling
public class IssueIsekaiApplication {
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("import-vanilla-assets")) {
            importVanillaAssets(args);
            return;
        }
        SpringApplication.run(IssueIsekaiApplication.class, args);
    }

    private static void importVanillaAssets(String[] args) {
        Path clientJar = Path.of(argument(args, "--client-jar"));
        String version = argument(args, "--minecraft-version");
        SpringApplication application = new SpringApplication(IssueIsekaiApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext context = application.run("import-vanilla-assets")) {
            ResourcePackService.Revision revision = context.getBean(ResourcePackService.class)
                    .importVanilla(clientJar, version);
            System.out.println("Imported vanilla assets revision " + revision.id());
        }
    }

    private static String argument(String[] args, String name) {
        for (int index = 1; index < args.length; index++) {
            if (args[index].equals(name) && index + 1 < args.length) {
                return args[index + 1];
            }
            if (args[index].startsWith(name + "=")) {
                return args[index].substring(name.length() + 1);
            }
        }
        throw new IllegalArgumentException(name + " is required");
    }

    @Bean
    HttpClient httpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }
}
