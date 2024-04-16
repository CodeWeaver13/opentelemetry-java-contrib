/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.contrib.aws.resource;

import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_VERSION;
import static io.opentelemetry.semconv.incubating.CloudIncubatingAttributes.CLOUD_PLATFORM;
import static io.opentelemetry.semconv.incubating.CloudIncubatingAttributes.CLOUD_PROVIDER;
import static io.opentelemetry.semconv.incubating.CloudIncubatingAttributes.CloudPlatformValues.AWS_ELASTIC_BEANSTALK;
import static io.opentelemetry.semconv.incubating.CloudIncubatingAttributes.CloudProviderValues.AWS;
import static io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes.SERVICE_INSTANCE_ID;
import static io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes.SERVICE_NAMESPACE;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.SchemaUrls;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A factory for a {@link Resource} which provides information about the current EC2 instance if
 * running on AWS Elastic Beanstalk.
 */
public final class BeanstalkResource {

  private static final Logger logger = Logger.getLogger(BeanstalkResource.class.getName());

  private static final String DEVELOPMENT_ID = "deployment_id";
  private static final String VERSION_LABEL = "version_label";
  private static final String ENVIRONMENT_NAME = "environment_name";
  private static final String BEANSTALK_CONF_PATH = "/var/elasticbeanstalk/xray/environment.conf";
  private static final JsonFactory JSON_FACTORY = new JsonFactory();

  private static final Resource INSTANCE = buildResource();

  /**
   * Returns a factory for a {@link Resource} which provides information about the current EC2
   * instance if running on AWS Elastic Beanstalk.
   */
  public static Resource get() {
    return INSTANCE;
  }

  private static Resource buildResource() {
    return buildResource(BEANSTALK_CONF_PATH);
  }

  // Visible for testing
  static Resource buildResource(String configPath) {
    File configFile = new File(configPath);
    if (!configFile.exists()) {
      return Resource.empty();
    }

    AttributesBuilder attrBuilders = Attributes.builder();
    try (JsonParser parser = JSON_FACTORY.createParser(configFile)) {
      parser.nextToken();

      if (!parser.isExpectedStartObjectToken()) {
        logger.log(Level.WARNING, "Invalid Beanstalk config: ", configPath);
        return Resource.create(attrBuilders.build(), SchemaUrls.V1_25_0);
      }

      while (parser.nextToken() != JsonToken.END_OBJECT) {
        parser.nextValue();
        String value = parser.getText();
        switch (parser.currentName()) {
          case DEVELOPMENT_ID:
            attrBuilders.put(SERVICE_INSTANCE_ID, value);
            break;
          case VERSION_LABEL:
            attrBuilders.put(SERVICE_VERSION, value);
            break;
          case ENVIRONMENT_NAME:
            attrBuilders.put(SERVICE_NAMESPACE, value);
            break;
          default:
            parser.skipChildren();
        }
      }
    } catch (IOException e) {
      logger.log(Level.WARNING, "Could not parse Beanstalk config.", e);
      return Resource.empty();
    }

    attrBuilders.put(CLOUD_PROVIDER, AWS);
    attrBuilders.put(CLOUD_PLATFORM, AWS_ELASTIC_BEANSTALK);

    return Resource.create(attrBuilders.build(), SchemaUrls.V1_25_0);
  }

  private BeanstalkResource() {}
}
