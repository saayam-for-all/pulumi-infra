package com.saayam.infrastructure;

import com.pulumi.aws.eks.Cluster;
import com.pulumi.aws.eks.ClusterArgs;
import com.pulumi.aws.eks.inputs.ClusterVpcConfigArgs;
import com.pulumi.aws.iam.OpenIdConnectProvider;
import com.pulumi.aws.iam.OpenIdConnectProviderArgs;
import com.pulumi.core.Output;
import com.pulumi.resources.ComponentResource;
import com.pulumi.resources.CustomResourceOptions;
import lombok.Getter;

import java.util.Map;
import java.util.Optional;

@Getter
public class EKSCluster extends ComponentResource {

    private final Cluster cluster;

    public EKSCluster(String name, Naming naming, Networking networking, EKSRole eksRole) {
        super("timely:infrastructure:EKSCluster", naming.annotate(name));
        CustomResourceOptions parent = CustomResourceOptions.builder().parent(this).build();
        cluster = new Cluster(naming.annotate("eks-cluster"),
            ClusterArgs.builder()
                .roleArn(eksRole.getEksRole().arn()) // Specify the ARN for your EKS role
                .vpcConfig(ClusterVpcConfigArgs.builder()
                    .subnetIds(Output.all(
                            networking.getPublicSubnet().id(),
                            networking.getPublicSubnet2().id(),
                            networking.getPrivateSubnet().id(),
                            networking.getPrivateSubnet2().id()))
                    .build())
                .tags(Map.of(
                        "Environment",  naming.getStackName()))
                .build(),
            parent);

        Output<Optional<String>> oidcURL = cluster.identities()
            .applyValue(identities -> identities.getFirst().oidcs().getFirst().issuer());

        Output<OpenIdConnectProvider> oidcProvider = oidcURL.applyValue(url ->
            new OpenIdConnectProvider("oidcProvider",
                OpenIdConnectProviderArgs.builder()
                    .clientIdLists("sts.amazonaws.com")
                    .url(url.orElseThrow())
                    .thumbprintLists("9e99a48a9960b14926bb7f3b02e22da2b0ab7280")
                    .build(), parent));
    }
}
