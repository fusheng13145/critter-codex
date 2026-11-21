package com.just.critter.adopt.service;

/**
 * 一次到期任务的结果，便于写日志与重跑核对。
 */
public record AdoptionExpiryResult(int reminded, int expired) {
}
