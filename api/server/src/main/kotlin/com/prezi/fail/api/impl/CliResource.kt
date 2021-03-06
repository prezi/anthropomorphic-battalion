package com.prezi.fail.api.impl

import com.linkedin.restli.server.annotations.RestLiActions
import com.linkedin.restli.server.annotations.Action
import com.linkedin.data.template.StringArray
import com.linkedin.data.template.StringMap
import com.linkedin.restli.server.annotations.ActionParam
import com.prezi.fail.api.cli.ApiCliActions
import com.prezi.fail.api.cli.ApiCliOptions
import com.prezi.fail.config.FailConfig
import com.prezi.fail.config.FailConfigKey
import com.prezi.fail.extensions.*
import com.prezi.fail.api.CliResult
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import org.apache.commons.cli.ParseException
import ch.qos.logback.core.Context
import com.prezi.fail.config.updateLoggerLevels
import com.prezi.fail.config.getRootLogLevel
import com.prezi.fail.config.setRootLogLevel
import com.prezi.fail.logging.LogCollector


[RestLiActions(namespace = "com.prezi.fail.api", name = "Cli")]
public class CliResource {
    val logger = LoggerFactory.getLogger(javaClass)!!

    fun clientShouldPrintHelp(validCommand: Boolean, output: String = "") = CliResult()
            .setExitCode(if (validCommand) 0 else 1)!!
            .setOutput(output)!!
            .setValidCommandLine(validCommand)!!

    [Action(name="RunCli")]
    public fun runCli([ActionParam("args")] _args: StringArray,
                      [ActionParam("systemProperties")] systemProperties: StringMap): CliResult {
        val logCollector = LogCollector().start()
        val args = _args.copyToArray()
        val options = ApiCliOptions()
        val cliConfig = FailConfig()
        val originalRootLogLevel = getRootLogLevel()

        val result = try {
            val cmdLine = options.parse(args)
            FailConfigKey.values().forEach { cliConfig.applyOptionsToSystemProperties(cmdLine, it, it.opt, systemProperties) }
            updateLoggerLevels(cliConfig.withConfigMap(systemProperties) as FailConfig)
            if (cmdLine.hasOption(options.help)) {
                clientShouldPrintHelp(validCommand = true, output = options.printHelp(ApiCliActions.cmdLineSyntax))
            } else {
                val action = ApiCliActions(systemProperties).parsePositionalArgs(args)
                if (action == null) {
                    clientShouldPrintHelp(validCommand = false)
                } else {
                    action.run()
                    CliResult().setExitCode(action.exitCode)!!.setValidCommandLine(true)!!.setOutput("")!!
                }
            }
        } catch(e: ParseException) {
            clientShouldPrintHelp(false, e.getMessage()!!)
        } catch(e: Throwable) {
            logger.error("Internal server error while trying to run ${args.join(" ")} with system properties ${systemProperties}", e)
            throw e
        } finally {
            setRootLogLevel(originalRootLogLevel)
        }

        result.setOutput((result.getOutput() + logCollector.stopAndGetEncodedMessages()).trim())

        return result
    }
}
