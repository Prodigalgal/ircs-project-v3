package com.prodigalgal.ircs.ops;

record QueueState(int messageCount, int consumerCount) {
    static QueueState empty() {
        return new QueueState(0, 0);
    }
}
