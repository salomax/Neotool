package io.github.salomax.neotool.assistant.infra.kafka

import org.slf4j.LoggerFactory

interface BankingReaderRequestedRoutingService {
    fun onCreditCardBill(request: AssistantBankingReaderRequestedEvent)

    fun onBankStatement(request: AssistantBankingReaderRequestedEvent)

    fun onInvestmentIrReport(request: AssistantBankingReaderRequestedEvent)
}

class NoOpBankingReaderRequestedRoutingService : BankingReaderRequestedRoutingService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun onCreditCardBill(request: AssistantBankingReaderRequestedEvent) {
        log.info("Received CREDIT_CARD_BILL reader request for job_id={} (BKL-010 pending)", request.job_id)
    }

    override fun onBankStatement(request: AssistantBankingReaderRequestedEvent) {
        log.info("Received BANK_STATEMENT reader request for job_id={} (BKL-010 pending)", request.job_id)
    }

    override fun onInvestmentIrReport(request: AssistantBankingReaderRequestedEvent) {
        log.info("Received INVESTMENT_IR_REPORT reader request for job_id={} (BKL-010 pending)", request.job_id)
    }
}
