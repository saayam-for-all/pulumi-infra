package com.saayaam.infrastructure;

import com.pulumi.aws.ecr.Repository;
import com.pulumi.aws.ecr.RepositoryArgs;
import com.pulumi.resources.ComponentResource;
import com.pulumi.resources.CustomResourceOptions;
import lombok.Getter;

@Getter
public class ECRRepository extends ComponentResource {

  private final Repository repository;
  public ECRRepository(String name, Naming naming) {
    super("timely:infrastructure:ECRRepository", naming.annotate(name));
    repository = new Repository(
        naming.annotate(name),
        RepositoryArgs.builder()
            .name(name)
            .build(),
        CustomResourceOptions
            .builder()
            .parent(this)
            .build());
  }
}
