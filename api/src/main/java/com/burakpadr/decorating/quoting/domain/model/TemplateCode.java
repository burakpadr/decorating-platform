package com.burakpadr.decorating.quoting.domain.model;

/**
 * §13's eleven notification templates — seven for the customer, four for the operator.
 *
 * <p>The name is the filename: {@code notifications/tr/<CODE>.txt}, and it is what
 * {@code notification.template_code} records. The text lives in the deployed artifact rather than in a
 * row so that a rollback takes the wording with it and a diff shows what changed.
 *
 * <p>{@code SmsTemplatesTest} holds this list against the files on disk. A code with no file fails at
 * send time, in production, on the one message that mattered.
 */
public enum TemplateCode {
	ESTIMATE_SMS,
	QUOTE_READY,
	RECAPTURE_NEEDED,
	SURVEY_NEEDED,
	EXPIRY_REMINDER,
	QUOTE_EXPIRED,
	ACCEPT_CONFIRMED,
	OPERATOR_NEW_REQUEST,
	OPERATOR_QUOTE_ACCEPTED,
	OPERATOR_CALLBACK_OVERDUE,
	OPERATOR_DELETION_REQUEST
}
