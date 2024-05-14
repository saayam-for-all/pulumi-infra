package com.saayaam.infrastructure;

import com.pulumi.Config;
import com.pulumi.Pulumi;
import com.pulumi.core.Output;
import com.saayaam.infrastructure.metadata.AZ;

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

            AZ az1 = AZ.fromAZString(config.require("az1"));
            AZ az2 = AZ.fromAZString(config.require("az2"));
            String productName = config.require("productName");

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

            EKSRole eksRole = new EKSRole("eks-role", naming);
            EKSCluster eksCluster = new EKSCluster("eks-cluster", naming, networking, eksRole);
            EKSNodeGroup eksNodeGroup = new EKSNodeGroup(
                "eks-node-group", naming, networking, eksCluster);

            ctx.export("eksClusterEndpointOidcIssuerUrl", eksCluster.getCluster().identities()
                .applyValue(identities -> identities.getFirst().oidcs().getFirst().issuer().orElseThrow()));
            ctx.export("eksClusterName", eksCluster.getCluster().name());
        });
    }
}
