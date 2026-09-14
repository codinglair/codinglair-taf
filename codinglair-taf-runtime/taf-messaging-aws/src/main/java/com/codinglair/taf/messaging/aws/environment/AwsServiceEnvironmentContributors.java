package com.codinglair.taf.messaging.aws.environment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable service-keyed view of discovered AWS environment contributors. */
public final class AwsServiceEnvironmentContributors {
  private final Map<String, AwsServiceEnvironmentContributor> contributors;

  public AwsServiceEnvironmentContributors(List<AwsServiceEnvironmentContributor> discovered) {
    var indexed = new LinkedHashMap<String, AwsServiceEnvironmentContributor>();
    discovered.forEach(
        contributor -> {
          String service = contributor.service();
          if (service == null || service.isBlank())
            throw new IllegalArgumentException("AWS contributor service must not be blank");
          if (indexed.putIfAbsent(service, contributor) != null)
            throw new IllegalArgumentException("Duplicate AWS contributor service: " + service);
        });
    contributors = Map.copyOf(indexed);
  }

  public List<AwsServiceEnvironmentContributor> all() {
    return List.copyOf(contributors.values());
  }

  public AwsServiceEnvironmentContributor require(String service) {
    AwsServiceEnvironmentContributor contributor = contributors.get(service);
    if (contributor == null) throw new IllegalArgumentException("Unknown AWS service: " + service);
    return contributor;
  }
}
