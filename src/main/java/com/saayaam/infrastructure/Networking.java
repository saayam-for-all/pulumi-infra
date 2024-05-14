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
    igw = igw(naming, parent, vpc.id());

    // AZ 1
    publicSubnet = publicSubnet(
        "public-subnet", naming, parent, args.getPublicCIDR(), args.getAz1(), vpc.id());
    privateSubnet = privateSubnet(
        "private-subnet", naming, parent, args.getPrivateCIDR(), args.getAz1(), vpc.id());
    publicRouteTable = publicRouteTable(
        "public-route-table", naming, parent, publicSubnet, igw, vpc.id());
    var natEip = eip("eip-for-NAT-gw", naming, parent);
    var natGW = natGW("public-nat-gw", naming, publicSubnet, natEip, parent);
    routeToNATFromPrivateSubnet("route-to-nat", naming, parent, vpc.id(), natGW, privateSubnet);


    // AZ 2
    publicSubnet2 = publicSubnet(
        "public-subnet-2", naming, parent, args.getPublicCIDR2(), args.getAz2(), vpc.id());
    privateSubnet2 = privateSubnet(
        "private-subnet2", naming, parent, args.getPrivateCIDR2(), args.getAz2(), vpc.id());
    publicRouteTable2 = publicRouteTable(
        "public-route-table-2", naming, parent, publicSubnet2, igw, vpc.id());
    var natEip2 = eip("eip-for-NAT-gw-2", naming, parent);
    var natGW2 = natGW("public-nat-gw-2", naming, publicSubnet2, natEip2, parent);
    routeToNATFromPrivateSubnet("route-to-nat2", naming, parent, vpc.id(), natGW2, privateSubnet2);


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
      Naming naming, CustomResourceOptions parent, Output<String> vpcID) {
    String igwName = naming.annotate("igw");
    return new InternetGateway(
        igwName,
        InternetGatewayArgs.builder()
            .vpcId(vpcID)
            .build(),
        parent);
  }

  private static RouteTable publicRouteTable(
      String name, Naming naming, CustomResourceOptions parent,
      Subnet subnet, InternetGateway igw, Output<String> vpcID) {
    String publicRouteTableName = naming.annotate(name);
    RouteTable routeTable = new RouteTable(
        publicRouteTableName,
        RouteTableArgs.builder()
            .vpcId(vpcID)
            .build(),
        parent);

    // Create a route in the Route Table that directs internet-bound traffic to the IGW
    var defaultRoute = new Route(
        naming.annotate(name+ "-route-to-internet"),
        RouteArgs.builder()
            .routeTableId(routeTable.id())
            .destinationCidrBlock("0.0.0.0/0")
            .gatewayId(igw.id())
            .build(),
        parent);

    var publicRouteTableAssociation = new RouteTableAssociation(
        naming.annotate(name + "-association"),
        RouteTableAssociationArgs.builder()
            .routeTableId(routeTable.id())
            .subnetId(subnet.id())
            .build(),
        parent);
    return routeTable;
  }

  private static Subnet publicSubnet(
      String name, Naming naming, CustomResourceOptions parent,
      Output<String> cidrBlock, AZ az, Output<String> vpcID) {
    String publicSubnetName = naming.annotate(name);
    return  new Subnet(
        publicSubnetName,
        SubnetArgs.builder()
            .vpcId(vpcID)
            .cidrBlock(cidrBlock)
            .mapPublicIpOnLaunch(true) // Makes it a public subnet
            .availabilityZone(az.value())
            .tags(Map.of(
                "kubernetes.io/role/elb", "1",
                "Name", publicSubnetName,
                "Environment", naming.getStackName()))
            .build(),
        parent);
  }

  private static Subnet privateSubnet(
      String name, Naming naming, CustomResourceOptions parent,
      Output<String> cidrBlock, AZ az, Output<String> vpcID){
    String privateSubnetName = naming.annotate(name);
    return new Subnet(
        privateSubnetName,
        SubnetArgs.builder()
            .vpcId(vpcID)
            .cidrBlock(cidrBlock)
            .mapPublicIpOnLaunch(false) // Makes it a private subnet
            .availabilityZone(az.value())
            .tags(Map.of(
                "kubernetes.io/role/internal-elb", "1",
                "Name", privateSubnetName,
                "Environment", naming.getStackName()))
            .build(),
        parent);
  }

  private static Eip eip(String name, Naming naming, CustomResourceOptions parent) {
    String eipNatGateway2 = naming.annotate(name);
    return new Eip(
        eipNatGateway2,
        EipArgs.builder()
            .domain("vpc")
            .build(),
        parent);
  }

  private static NatGateway natGW(
      String name, Naming naming, Subnet subnet, Eip eip, CustomResourceOptions parent) {
    String publicNatGateway2 = naming.annotate(name);
    return new NatGateway(
        publicNatGateway2,
        NatGatewayArgs.builder()
            .subnetId(subnet.id())
            .allocationId(eip.id())
            .build(),
        parent);
  }

  private static void routeToNATFromPrivateSubnet(
      String name, Naming naming,
      CustomResourceOptions parent, Output<String> vpcID,
      NatGateway natGW, Subnet subnet) {
    // Create a Route Table for the private subnet
    var privateRouteTable = new RouteTable(
        naming.annotate(name + "-table"),
        RouteTableArgs.builder()
            .vpcId(vpcID)
            .build(),
        parent);

    // Add a route to the NAT Gateway in the private Route Table
    var natRoute = new Route(
        naming.annotate(name + "-route-to-nat"),
        RouteArgs.builder()
            .routeTableId(privateRouteTable.id())
            .destinationCidrBlock("0.0.0.0/0")
            .natGatewayId(natGW.id())
            .build(),
        parent);

    String privateRouteTableAssociationName
        = naming.annotate(name + "-route-table-association");
    var privateRouteTableAssociation = new RouteTableAssociation(
        privateRouteTableAssociationName,
        RouteTableAssociationArgs.builder()
            .routeTableId(privateRouteTable.id())
            .subnetId(subnet.id())
            .build(),
        parent);
  }

}
