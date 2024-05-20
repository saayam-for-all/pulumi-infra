# README #

## What is this repository for? ##

### This repository will manage all the underlying infrastructure components that are not application-specific but are essential for running those applications. This includes: ###

* VPC, Subnets, and Networking: The network infrastructure including the VPC, subnets, NAT gateways, and route tables.
* EKS Cluster: Configuration of the Kubernetes cluster, including versions, subnets, and associated security groups.
* IAM Roles: Necessary for EKS and node groups, along with any IAM policies for logging, monitoring, and other AWS services.
* Node Groups: Definitions of various node groups that might have different scaling properties or instance types.
* Shared Services: Things like RDS databases, ElasticCache clusters, or shared S3 buckets.
* Security Configurations: Security group definitions, access roles, any firewall configurations, etc.

This repository’s main goal is to set up and configure resources that provide a platform for deploying applications.

This README would normally document whatever steps are necessary to get your application up and running.


