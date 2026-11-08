package com.example.ordermgmt.contract.events;

import java.math.BigDecimal;

/**
 * One line of an order as it travels inside events.
 *
 * @param productId id of the product
 * @param quantity  how many units
 * @param unitPrice price of one unit
 */
public record OrderLine(String productId, int quantity, BigDecimal unitPrice) {
}
