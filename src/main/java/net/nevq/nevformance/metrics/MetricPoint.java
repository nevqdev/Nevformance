package net.nevq.nevformance.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Represents a single data point with timestamp and value
 */
public record MetricPoint(long timestamp, double value) {
}

/**
 * Fixed-size circular buffer to store metric time series data
 */
class CircularMetricBuffer {
    private final MetricPoint[] buffer;
    private int size = 0;
    private int head = 0; // Points to the next slot to write
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public CircularMetricBuffer(int capacity) {
        buffer = new MetricPoint[capacity];
    }

    public void add(MetricPoint point) {
        lock.writeLock().lock();
        try {
            buffer[head] = point;
            head = (head + 1) % buffer.length;
            if (size < buffer.length) {
                size++;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<MetricPoint> getPoints() {
        lock.readLock().lock();
        try {
            List<MetricPoint> result = new ArrayList<>(size);

            if (size == buffer.length) {
                // Buffer is full, start from head (oldest data)
                for (int i = 0; i < buffer.length; i++) {
                    int index = (head + i) % buffer.length;
                    result.add(buffer[index]);
                }
            } else {
                // Buffer is not full yet, start from 0
                for (int i = 0; i < size; i++) {
                    result.add(buffer[i]);
                }
            }

            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<MetricPoint> getPointsInRange(long startTime, long endTime) {
        lock.readLock().lock();
        try {
            List<MetricPoint> allPoints = getPoints();
            List<MetricPoint> filteredPoints = new ArrayList<>();

            for (MetricPoint point : allPoints) {
                if (point.timestamp() >= startTime && point.timestamp() <= endTime) {
                    filteredPoints.add(point);
                }
            }

            return filteredPoints;
        } finally {
            lock.readLock().unlock();
        }
    }

    public MetricPoint getLatestPoint() {
        lock.readLock().lock();
        try {
            if (size == 0) {
                return null;
            }

            int latestIndex = (head - 1 + buffer.length) % buffer.length;
            return buffer[latestIndex];
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getSize() {
        lock.readLock().lock();
        try {
            return size;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getCapacity() {
        return buffer.length;
    }
}