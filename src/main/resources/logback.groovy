import ch.qos.logback.classic.boolex.GEventEvaluator
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.FileAppender
import ch.qos.logback.core.filter.EvaluatorFilter

import static ch.qos.logback.classic.Level.INFO
import static ch.qos.logback.core.spi.FilterReply.DENY
import static ch.qos.logback.core.spi.FilterReply.NEUTRAL

scan("30 seconds")

def now = new Date().format("YYMMdd-HH.mm.ss.SSS")
def layoutPattern = "%-5level %logger{16} %method - %msg%n"

appender("FILE", FileAppender) {
    filter(EvaluatorFilter) {
        evaluator(GEventEvaluator) {
            expression = 'e.level.toInt() >= WARN.toInt()'
        }
        onMatch = NEUTRAL
        onMismatch = DENY
    }
    file = "logs/${now}.log"
    append = false
    encoder(PatternLayoutEncoder) {
        pattern = layoutPattern
    }
}

appender("STDOUT", ConsoleAppender) {
    encoder(PatternLayoutEncoder) {
        pattern = layoutPattern
    }
}
root(INFO, ["STDOUT", "FILE"])
