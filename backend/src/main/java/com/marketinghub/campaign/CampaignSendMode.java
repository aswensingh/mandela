package com.marketinghub.campaign;

/**
 * How a campaign's messages are delivered to WhatsApp:
 *
 * <ul>
 *   <li>{@link #TEMPLATE} — sent as an approved Meta message template. The only way to reach a
 *       customer OUTSIDE the 24-hour customer-service window, i.e. real cold marketing. Requires
 *       a template that is actually APPROVED in the tenant's WhatsApp Business Account.</li>
 *   <li>{@link #FREE_TEXT} — sent as a free-form text message. Needs no template/approval, but
 *       Meta only delivers it to customers who messaged the business within the last 24 hours;
 *       everyone else is rejected. Good for broadcasting to recently-active customers.</li>
 * </ul>
 */
public enum CampaignSendMode {
    TEMPLATE,
    FREE_TEXT
}
