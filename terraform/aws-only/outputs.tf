output "ec2_instance_id" {
  value = aws_instance.order_batch_ec2.id
}

output "eks_cluster_name" {
  value = aws_eks_cluster.orders_eks_cluster.name
}

output "eks_cluster_endpoint" {
  value = aws_eks_cluster.orders_eks_cluster.endpoint
}
