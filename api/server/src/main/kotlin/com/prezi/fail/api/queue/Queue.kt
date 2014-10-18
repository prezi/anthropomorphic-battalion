package com.prezi.fail.api.queue

import com.amazonaws.services.sqs.AmazonSQSClient
import com.prezi.fail.api.ScheduledFailure
import com.amazonaws.services.sqs.model.SendMessageRequest
import com.prezi.fail.api.Run
import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import com.prezi.fail.api.db.DB
import com.prezi.fail.api.db.DBRun
import org.slf4j.LoggerFactory
import com.amazonaws.AmazonServiceException
import com.prezi.fail.api.Api
import com.prezi.fail.api.RunBuilders
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityRequest
import com.amazonaws.services.sqs.AmazonSQS

class Queue(val client: AmazonSQS = AmazonSQSClient(), val name: String = "fail-scheduled-runs") {
    val url = client.getQueueUrl(name)?.getQueueUrl()

    internal val logger = LoggerFactory.getLogger(javaClass)

    public fun putRun(r: Run) {
        println("putting run ${r}")
        val req = SendMessageRequest().withQueueUrl(url)
        client.sendMessage(req.withMessageBody(r.getId()))
    }
    public fun receiveRunAnd(action: (Run) -> Unit): Unit {
        try {
            val recvReq = ReceiveMessageRequest().withQueueUrl(url).withMaxNumberOfMessages(1)
            val recvMsg = client.receiveMessage(recvReq)
            val msg = recvMsg.getMessages().first
            try {
                if (msg == null) {
                    logger.debug("Received empty message from SQS")
                    return
                }
                val run = Api().withClient({ client ->
                    client.sendRequest(RunBuilders().get().id(msg.getBody()).build()).getResponseEntity()
                })
                if (run == null) {
                    logger.error("run_not_found ${msg.getBody()}")
                    client.deleteMessage(url, msg.getReceiptHandle())
                    return
                }
                println(run)
                val expectedDuration = run.getScheduledFailure().getDuration()
                client.changeMessageVisibility(ChangeMessageVisibilityRequest()
                        .withQueueUrl(url)
                        .withReceiptHandle(msg.getReceiptHandle())
                        .withVisibilityTimeout(expectedDuration))
                try {
                    action(run)
                } catch (e: Exception) {
                    logger.error("exception_during_action ${e}")
                    return
                }
            } catch (e: Exception) {
                logger.error("exception_during_message_handling ${e}")
            } finally {
                client.deleteMessage(url, msg?.getReceiptHandle())
            }
        } catch (e: Exception) {
            logger.error("exception_during_sqs_receive ${e}")
            return
        }
    }
}