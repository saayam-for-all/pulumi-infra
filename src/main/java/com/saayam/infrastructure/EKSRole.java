package com.saayam.infrastructure;

import com.pulumi.aws.iam.Role;
import com.pulumi.aws.iam.RoleArgs;
import com.pulumi.aws.iam.RolePolicyAttachment;
import com.pulumi.aws.iam.RolePolicyAttachmentArgs;
import com.pulumi.resources.*;
import lombok.Getter;

@Getter
public class EKSRole extends ComponentResource {

    private final Role eksRole;

    public EKSRole(String name, Naming naming) {
        super("timely:infrastructure:EKSRole", naming.annotate(name));
        CustomResourceOptions parent = CustomResourceOptions.builder().parent(this).build();
        var assumeRolePolicy = "{\n" +
                "  \"Version\": \"2012-10-17\",\n" +
                "  \"Statement\": [\n" +
                "    {\n" +
                "      \"Effect\": \"Allow\",\n" +
                "      \"Principal\": {\"Service\": \"eks.amazonaws.com\"},\n" +
                "      \"Action\": \"sts:AssumeRole\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        eksRole = new Role(
            naming.annotate("eksRole"),
            RoleArgs.builder()
                .assumeRolePolicy(assumeRolePolicy)
                .build(),
            parent);

        // Attach the AmazonEKSClusterPolicy to the role
        new RolePolicyAttachment(
            naming.annotate("eksClusterPolicyAttachment"),
            RolePolicyAttachmentArgs.builder()
                .role(eksRole.name())
                .policyArn("arn:aws:iam::aws:policy/AmazonEKSClusterPolicy")
                .build(),
            parent);

        // Attach the AmazonEKSVPCResourceController policy (required for the VPC CNI plugin)
        new RolePolicyAttachment(
            naming.annotate("eksVpcResourceControllerPolicyAttachment"),
            RolePolicyAttachmentArgs.builder()
                .role(eksRole.name())
                .policyArn("arn:aws:iam::aws:policy/AmazonEKSVPCResourceController")
                .build(),
            parent);
    }
}
