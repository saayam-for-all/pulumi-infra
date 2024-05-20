package com.saayam.infrastructure;

import com.google.common.base.Splitter;
import com.pulumi.Config;
import com.pulumi.Pulumi;
import com.pulumi.core.Output;
import com.saayam.infrastructure.metadata.AZ;
import com.saayam.infrastructure.metadata.InfrastructureModule;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class App {

    public static void main(String[] args) {
        Pulumi.run(ctx -> {
            Config config = ctx.config();
            System.out.println(
                "product = " + config.require("productName") + '\n' +
                "environment  = " + ctx.stackName());
            Naming naming = new Naming(
                config.require("productName"),
                ctx.stackName());

            Set<InfrastructureModule> modules = Optional.of(config.require("modules"))
                .map(m -> Splitter.on(',')
                    .trimResults()
                    .omitEmptyStrings()
                    .splitToList(m))
                .stream()
                .flatMap(List::stream)
                .map(String::toLowerCase)
                .map(InfrastructureModule::valueOf)
                .collect(Collectors.toSet());

            AZ az1 = AZ.fromAZString(config.require("az1"));
            AZ az2 = AZ.fromAZString(config.require("az2"));
            Networking networking = new Networking(
                "networking",
                Networking.NetworkingArgs.builder()
                    .vipCIDR(Output.of(config.require("vpcCIDR")))
                    .publicCIDR(Output.of(config.require("publicCIDR")))
                    .publicCIDR2(Output.of(config.require("publicCIDR2")))
                    .privateCIDR(Output.of(config.require("privateCIDR")))
                    .privateCIDR2(Output.of(config.require("privateCIDR2")))
                    .az1(az1)
                    .az2(az2)
                    .build(),
                naming);

            if (modules.contains(InfrastructureModule.eks)) {
                EKSRole eksRole = new EKSRole("eks-role", naming);
                EKSCluster eksCluster = new EKSCluster("eks-cluster", naming, networking, eksRole);
                EKSNodeGroup eksNodeGroup = new EKSNodeGroup(
                    "eks-node-group", naming, networking, eksCluster);

                ctx.export("eksClusterEndpointOidcIssuerUrl", eksCluster.getCluster().identities()
                    .applyValue(identities -> identities.getFirst().oidcs().getFirst().issuer().orElseThrow()));
                ctx.export("eksClusterName", eksCluster.getCluster().name());
            }
        });
    }
}
