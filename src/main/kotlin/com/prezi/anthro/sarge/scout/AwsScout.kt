package com.prezi.anthro.sarge.scout

import com.prezi.anthro.sarge.SargeConfig
import com.prezi.anthro.sarge.Aws
import com.amazonaws.services.ec2.model.Filter
import com.amazonaws.services.ec2.model.DescribeInstancesRequest

abstract class AwsScout(config: SargeConfig) : Scout {
    val aws = Aws(config)

    abstract fun buildFilters(by: String): List<Filter>

    override fun findTargets(by: String) =
            aws.ec2().describeInstances(DescribeInstancesRequest().withFilters(buildFilters(by)))
                    ?.getReservations()!!.flatMap{ it.getInstances()!!}
                    .filter{ it.getState()?.getName() == "running" }.map{ it.getPublicDnsName()!!}
}
