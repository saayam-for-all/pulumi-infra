package com.saayam.infrastructure;

import com.google.common.base.Strings;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

@Getter
public class Naming {

  private final String productName;
  private final String stackName;

  Naming(String productName, String stackName) {
    this.productName = productName;
    this.stackName = stackName;
  }

  public String annotate(String name, String... additionalSuffixes) {
    StringBuilder builder =  new StringBuilder(
        productName + '-' + name + '-' + stackName);
    Arrays.stream(additionalSuffixes)
        .forEach(item -> builder.append('-').append(item));
    return builder.toString();
  }

  public String annotateCamelCase(String name, String... additionalSuffixes) {
    StringBuilder builder = new StringBuilder();
    builder
        .append(StringUtils.capitalize(productName))
        .append(StringUtils.capitalize(name));

    Arrays.stream(additionalSuffixes)
        .forEach(item -> builder.append(StringUtils.capitalize(item)));
    return builder.toString();
  }

}
