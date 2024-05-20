package com.saayaam.infrastructure;

import com.pulumi.aws.ec2.*;
import com.pulumi.core.Output;
import com.pulumi.resources.*;
import com.saayaam.infrastructure.metadata.AZ;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;

import java.util.Map;

@Getter
public class Networking extends ComponentResource {
  @EqualsAndHashCode(callSuper = true)
  @Value
  @Builder
  public static class NetworkingArgs extends ResourceArgs {
    Output<String> vipCIDR;
    Output<String> publicCIDR;
    Output<String> publicCIDR2;
    Output<String> privateCIDR;
    Output<String> privateCIDR2;
    AZ az1;
    AZ az2;
  }

  private final Vpc vpc;
  private final Subnet publicSubnet;
  private final Subnet publicSubnet2;
  private final Subnet privateSubnet;
  private final Subnet privateSubnet2;
  private final InternetGateway igw;
  private final RouteTable publicRouteTable;
  private final RouteTable publicRouteTable2;

  public Networking(String name, NetworkingArgs args, Naming naming) {
    super("timely:infrastructure:Networking", naming.annotate(name));
    CustomResourceOptions parent = CustomResourceOptions.builder().parent(this).build();
    vpc = vpc(naming, parent, args.getVipCIDR());
    igw = igw(naming, vpc);

    // AZ 1
    publicSubnet = publicSubnet(
        "public-subnet", naming, vpc, args.getPublicCIDR(), args.getAz1());
    privateSubnet = privateSubnet(
        "private-subnet", naming, vpc, args.getPrivateCIDR(), args.getAz1());
    publicRouteTable = publicRouteTable(
        "public-route-table", naming, vpc, publicSubnet, igw);
    var natEip = eip("eip-for-NAT-gw", naming, vpc);
    var natGW = natGW("public-nat-gw", naming, publicSubnet, natEip, vpc);
    routeToNATFromPrivateSubnet("route-to-nat", naming, vpc, natGW, privateSubnet);


    // AZ 2
    publicSubnet2 = publicSubnet(
        "public-subnet-2", naming, vpc, args.getPublicCIDR2(), args.getAz2());
    privateSubnet2 = privateSubnet(
        "private-subnet2", naming, vpc, args.getPrivateCIDR2(), args.getAz2());
    publicRouteTable2 = publicRouteTable(
        "public-route-table-2", naming, vpc, publicSubnet2, igw);
    var natEip2 = eip("eip-for-NAT-gw-2", naming, vpc);
    var natGW2 = natGW("public-nat-gw-2", naming, publicSubnet2, natEip2, vpc);
    routeToNATFromPrivateSubnet("route-to-nat2", naming, vpc, natGW2, privateSubnet2);


  }

  private static Vpc vpc(Naming naming, CustomResourceOptions parent, Output<String> vipCIDR) {
    String vpcName = naming.annotate("vpc");
    return new Vpc(
        vpcName,
        VpcArgs.builder()
            .cidrBlock(vipCIDR)
            .enableDnsSupport(true)
            .enableDnsHostnames(true)
            .tags(Map.of(
                "Name", vpcName,
                "environment", naming.getStackName()))
            .build(),
        parent);
  }

  private static InternetGateway igw(
      Naming naming, Vpc vpc) {
    String igwName = naming.annotate("igw");
    return new InternetGateway(
        igwName,
        InternetGatewayArgs.builder()
            .vpcId(vpc.id())
            .build(),
        CustomResourceOptions.builder().parent(vpc).build());
  }

  private static RouteTable publicRouteTable(
      String name, Naming naming, Vpc vpc, Subnet subnet, InternetGateway igw) {
    CustomResourceOptions parent = CustomResourceOptions.builder().parent(vpc).build();
    String publicRouteTableName = naming.annotate(name);
    RouteTable routeTable = new RouteTable(
        publicRouteTableName,
        RouteTableArgs.builder()
            .vpcId(vpc.id())
            .build(),
        parent);

    // Create a route in the Route Table that directs internet-bound traffic to the IGW
    var defaultRoute = new Route(
        naming.annotate(name,"route-to-internet"),
        RouteArgs.builder()
            .routeTableId(routeTable.id())
            .destinationCidrBlock("0.0.0.0/0")
            .gatewayId(igw.id())
            .build(),
        parent);

    var publicRouteTableAssociation = new RouteTableAssociation(
        naming.annotate(name,"association"),
        RouteTableAssociationArgs.builder()
            .routeTableId(routeTable.id())
            .subnetId(subnet.id())
            .build(),
        parent);
    return routeTable;
  }

  private static Subnet publicSubnet(
      String name, Naming naming, Vpc vpc, Output<String> cidrBlock, AZ az) {
    String publicSubnetName = naming.annotate(name);
    return  new Subnet(
        publicSubnetName,
        SubnetArgs.builder()
            .vpcId(vpc.id())
            .cidrBlock(cidrBlock)
            .mapPublicIpOnLaunch(true) // Makes it a public subnet
            .availabilityZone(az.value())
            .tags(Map.of(
                "kubernetes.io/role/elb", "1",
                "Name", publicSubnetName,
                "Environment", naming.getStackName()))
            .build(),
        CustomResourceOptions.builder().parent(vpc).build());
  }

  private static Subnet privateSubnet(
      String name, Naming naming, Vpc vpc, Output<String> cidrBlock, AZ az){
    String privateSubnetName = naming.annotate(name);
    return new Subnet(
        privateSubnetName,
        SubnetArgs.builder()
            .vpcId(vpc.id())
            .cidrBlock(cidrBlock)
            .mapPublicIpOnLaunch(false) // Makes it a private subnet
            .availabilityZone(az.value())
            .tags(Map.of(
                "kubernetes.io/role/internal-elb", "1",
                "Name", privateSubnetName,
                "Environment", naming.getStackName()))
            .build(),
        CustomResourceOptions.builder().parent(vpc).build());
  }

  private static Eip eip(String name, Naming naming, Resource parent) {
    String eipNatGateway2 = naming.annotate(name);
    return new Eip(
        eipNatGateway2,
        EipArgs.builder()
            .domain("vpc")
            .build(),
        CustomResourceOptions.builder().parent(parent).build());
  }

  private static NatGateway natGW(
      String name, Naming naming, Subnet subnet, Eip eip, Resource parent) {
    String publicNatGateway2 = naming.annotate(name);
    return new NatGateway(
        publicNatGateway2,
        NatGatewayArgs.builder()
            .subnetId(subnet.id())
            .allocationId(eip.id())
            .build(),
        CustomResourceOptions.builder().parent(parent).build());
  }

  private static void routeToNATFromPrivateSubnet(
      String name, Naming naming, Vpc vpc, NatGateway natGW, Subnet subnet) {
    CustomResourceOptions parent = CustomResourceOptions.builder().parent(vpc).build();
    // Create a Route Table for the private subnet
    var privateRouteTable = new RouteTable(
        naming.annotate(name, "table"),
        RouteTableArgs.builder()
            .vpcId(vpc.id())
            .build(),
        parent);

    // Add a route to the NAT Gateway in the private Route Table
    var natRoute = new Route(
        naming.annotate(name, "route-to-nat"),
        RouteArgs.builder()
            .routeTableId(privateRouteTable.id())
            .destinationCidrBlock("0.0.0.0/0")
            .natGatewayId(natGW.id())
            .build(),
        parent);

    String privateRouteTableAssociationName
        = naming.annotate(name,"route-table-association");
    var privateRouteTableAssociation = new RouteTableAssociation(
        privateRouteTableAssociationName,
        RouteTableAssociationArgs.builder()
            .routeTableId(privateRouteTable.id())
            .subnetId(subnet.id())
            .build(),
        parent);
  }
}
