package org.camunda.bpm.extension.mail.spring.boot;

import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import org.camunda.bpm.extension.mail.config.JakartaMailProperties;
import org.camunda.bpm.extension.mail.config.MailConfiguration;
import org.camunda.bpm.extension.mail.config.MailConfigurationFactory;
import org.camunda.bpm.extension.mail.dto.Mail;
import org.camunda.bpm.extension.mail.notification.MailNotificationService;
import org.camunda.bpm.extension.mail.notification.MessageHandler;
import org.camunda.bpm.extension.mail.service.MailService;
import org.camunda.bpm.extension.mail.service.MailServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

@Configuration
public class MailConnectorConfiguration {
  private static final Logger LOG = LoggerFactory.getLogger(MailConnectorConfiguration.class);

  @Bean
  @Qualifier("mail-connector-configuration")
  @ConfigurationProperties(prefix = "camunda.bpm.plugin.mail")
  public Properties mailConnectorConfigurationProperties() {
    return new Properties();
  }

  @Bean
  public MailConfiguration mailConfiguration(
      @Qualifier("mail-connector-configuration") Properties properties) {
    LOG.debug("Appending 'mail.' prefix if missing:");
    Properties fixedProperties = new Properties();
    properties
        .stringPropertyNames()
        .forEach(
            key -> {
              if (key.startsWith("mail.")) {
                LOG.debug("Key '{}' starts with 'mail.', putting directly", key);
                fixedProperties.put(key, properties.getProperty(key));
              } else {
                String fixedKey = "mail." + key;
                LOG.debug("Key '{}' was fixed to '{}', putting fixed key", key, fixedKey);
                fixedProperties.put(fixedKey, properties.getProperty(key));
              }
            });
    JakartaMailProperties.set(fixedProperties);
    return MailConfigurationFactory.getInstance().get();
  }

  @Bean
  @ConditionalOnProperty(
      value = {
        "camunda.bpm.plugin.mail.notification.enabled",
        "camunda.bpm.plugin.mail.mail.notification.enabled"
      },
      matchIfMissing = true,
      havingValue = "true")
  public MailNotificationService mailNotificationService(
      MailConfiguration mailConfiguration,
      MailService mailService,
      Set<Consumer<Mail>> mailConsumers,
      Set<MessageHandler> messageHandlers) {
    SpringBootMailNotificationService notificationService =
        new SpringBootMailNotificationService(mailConfiguration, mailService);
    mailConsumers.forEach(notificationService::registerMailHandler);
    messageHandlers.forEach(notificationService::registerMessageHandler);
    return notificationService;
  }

  @Bean
  @DependsOn("mailConfiguration")
  public MailService mailService() {
    return MailServiceFactory.getInstance().get();
  }

  public static class SpringBootMailNotificationService extends MailNotificationService
      implements SmartLifecycle {

    public SpringBootMailNotificationService(
        MailConfiguration configuration, MailService mailService) {
      super(configuration, mailService);
    }
  }
}
