package com.sama.api.notice.object.DO.SC;

import com.google.common.collect.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * @author: huxh
 * @description: 集合超出指定大小时，通过移除优先级最低(Last)的元素来保持集合的有界性。
 * 考虑把排序放在容器结构内实现的原因：接收 List<E> elements的时机不是顺序的，同时插入、删除元素可能会造成容器内删掉的不是优先级最低的元素。
 * 不适用的PriorityQueue的原因：非线程安全
 * @datetime: 2024/6/12 15:08
 */
public class ElasticBoundedPrioritySet<E> implements Serializable {

    private static final long serialVersionUID = -1683828928262038154L;

    private final static Logger logger = LogManager.getLogger(ElasticBoundedPrioritySet.class);

    /**
     * 数据实际载体，线程安全
     */
    private final ConcurrentSkipListSet<E> set;

    /**
     * 容量下限，和bufferPad、accelerator一起使用
     * 在定时清理任务中，限定次数内加速加线程抢占，并防止无法急停（因为没有牺牲性能加锁）
     */
    private final int lowerCapacity;

    /**
     * 容量上限，即该阈值以内可以容忍（不清理）
     */
    private final int upperCapacity;

    // ************************* 清理策略1 *************************

    private final static int FIXED_MAX_THREAD_SIZE = 10;

    /**
     * 缓冲垫
     */
    private final int bufferPad;

    /**
     * 加速器（默认0）
     */
    private final AtomicInteger accelerator = new AtomicInteger(0);

    /**
     * 轻量加速锁
     */
    private final ReentrantLock acceleratorLock = new ReentrantLock();

    // ************************* 清理策略2 *************************

    /**
     * 临时缓冲区
     */
    private final CopyOnWriteArrayList<E> tempBuffer = new CopyOnWriteArrayList<>();

    // private final ReadWriteLock shrinkLock = new ReentrantReadWriteLock();

    public ElasticBoundedPrioritySet(Comparator<? super E>  comparator, int lowerCapacity, int upperCapacity) {
        this.set = new ConcurrentSkipListSet<>(comparator);
        this.lowerCapacity = lowerCapacity;
        this.upperCapacity = upperCapacity;
        // 比较合理的计算bufferPad方法
        this.bufferPad = (int) (lowerCapacity + (upperCapacity - lowerCapacity) * FIXED_MAX_THREAD_SIZE * 0.01);
    }

    @Deprecated
    public ElasticBoundedPrioritySet(Comparator<? super E>  comparator, int lowerCapacity, int upperCapacity, int bufferPad) {
        this.set = new ConcurrentSkipListSet<>(comparator);
        this.lowerCapacity = lowerCapacity;
        this.upperCapacity = upperCapacity;
        this.bufferPad = lowerCapacity + bufferPad;
    }

    public void add(E element) {
        set.add(element);
    }

    public void addAll(List<E> elements) {
        set.addAll(elements);
    }

    public List<E> nearAndFar(){
        if (!set.isEmpty()) {
            return Lists.newArrayList(set.first(), set.last());
        } else {
            return Lists.newArrayList();
        }
    }

    public int size() {
        return set.size();
    }

    // ************************* 原生方法都不要加锁，避免影响性能 *************************

    /**
     * 清理策略1：小于upperCapacity，不处理；大于upperCapacity且小于加速上限，增加线程；大于upperCapacity且大于加速上限，不处理；
     * 处理大小至bufferPad立刻停止
     *
     * @param subThreadPool 子线程池
     */
    public void elasticShrink(ThreadPoolTaskExecutor subThreadPool, String[] args) {
        if (set.size() <= upperCapacity){
            accelerator.set(0);
            return;
        }

        // 尝试获取锁，失败则直接结束此次任务，让获得锁的任务继续执行
        acceleratorLock.lock();
        try {
            if (accelerator.getAndIncrement() > 3) {
                logger.warn("预警！当前{}容器已经达到最大加速值，仍有{}条未清理！", args[0], set.size() - bufferPad);
                return;
            }
        } finally {
            acceleratorLock.unlock();
        }

        // 多线程（单条）删除优先级最低的元素
        for (int i = 0; i < FIXED_MAX_THREAD_SIZE; i++) {
            subThreadPool.execute(() -> {
                // logger.info("正在执行{}的清理子任务", args[0]);
                while (set.size() > bufferPad) {
                    set.pollLast();
                }
            });
        }

    }

    /**
     * 清理策略2：直接截断，当前容器大于upperCapacity时直接截断后面所有元素，保留lowerCapacity条数据
     *
     * @param args
     */
    public void truncateClean(String[] args){
        if (set.size() > upperCapacity) {
            synchronized (this) { // 使用同步块确保线程安全
                if (set.size() > upperCapacity) {
                    // 截断操作
                    logger.warn("预警！当前{}容器已经超过容器上限，仍有{}条未清理！", args[0], set.size() - lowerCapacity);
                    tempBuffer.clear();
                    tempBuffer.addAll(set.headSet(set.last(), true).stream().limit(lowerCapacity).collect(Collectors.toList()));
                    set.clear();
                    set.addAll(tempBuffer);
                    logger.info("截断完毕！当前{}容器保留{}条数据。", args[0], set.size());
                }
            }
        }
    }

    /**
     * 根据条件筛选元素
     * @param size
     * @param referenceElement
     * @return
     */
    public List<E> fetchAccordingCondition(Integer size, E referenceElement) {
        // 筛选比referenceElement优先级更高的元素（不移除）
        SortedSet<E> tailSet = set.headSet(referenceElement, false);
        return Lists.newArrayList(tailSet.stream().limit(size).iterator());
    }

}
