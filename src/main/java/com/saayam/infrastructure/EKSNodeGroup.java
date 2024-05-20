package com.saayam.infrastructure;

import com.pulumi.aws.eks.NodeGroupArgs;
import com.pulumi.aws.iam.Role;
import com.pulumi.aws.iam.RoleArgs;
import com.pulumi.aws.iam.RolePolicyAttachment;
import com.pulumi.aws.eks.NodeGroup;
import com.pulumi.aws.iam.RolePolicyAttachmentArgs;
import com.pulumi.core.Output;
import com.pulumi.aws.eks.inputs.NodeGroupScalingConfigArgs;
import com.pulumi.resources.ComponentResource;
import com.pulumi.resources.CustomResourceOptions;
import lombok.Getter;

@Getter
public class EKSNodeGroup extends ComponentResource {

  private final Role nodeRole;
  private final NodeGroup nodeGroup;

  public EKSNodeGroup(String name, Naming naming,
                      Networking networking, EKSCluster cluster) {
    super("timely:infrastructure:EKSNodeGroup", naming.annotate(name));
    CustomResourceOptions parent = CustomResourceOptions.builder().parent(this).build();
     // Create an IAM role for the EKS Node Group
    nodeRole = new Role(
        naming.annotate(name,"node-role"), RoleArgs.builder()
          .assumeRolePolicy("""
                    {
                        "Version": "2012-10-17",
                        "Statement": [{
                            "Effect": "Allow",
                            "Principal": {"Service": "ec2.amazonaws.com"},
                            "Action": "sts:AssumeRole"
                        }]
                    }
                """)
          .build(),
        parent);

    RolePolicyAttachment policyAttachment = new RolePolicyAttachment(
        naming.annotate(name, "node-group-policy-attachment"),
        RolePolicyAttachmentArgs.builder()
            .role(nodeRole.name())
            .policyArn("arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy")
            .build(),
        parent);

    RolePolicyAttachment cniPolicyAttachment = new RolePolicyAttachment(
        naming.annotate(name, "node-group-cni-policy-attachment"),
        RolePolicyAttachmentArgs.builder()
            .role(nodeRole.name())
            .policyArn("arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy")
            .build(),
        parent);

    RolePolicyAttachment ecrContainerRegistryPolicyAttachment = new RolePolicyAttachment(
        naming.annotate(name, "node-group-ecr-policy-attachment"),
        RolePolicyAttachmentArgs.builder()
            .role(nodeRole.name())
            .policyArn("arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly")
            .build(),
        parent);



    nodeGroup = new NodeGroup(
        naming.annotate(name, "node-group"),
        NodeGroupArgs.builder()
          .clusterName(cluster.getCluster().name())
          .nodeRoleArn(nodeRole.arn())
          .subnetIds(Output.all(
              networking.getPublicSubnet().id(),
              networking.getPublicSubnet2().id(),
              networking.getPrivateSubnet().id(),
              networking.getPrivateSubnet2().id()))
          .scalingConfig(
              NodeGroupScalingConfigArgs.builder()
                  .desiredSize(2)
                  .minSize(1)
                  .maxSize(3)
                  .build())
          .build(),
        parent);
  }
}
