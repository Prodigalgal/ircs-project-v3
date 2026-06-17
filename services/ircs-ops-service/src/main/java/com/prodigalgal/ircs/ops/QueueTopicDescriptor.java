package com.prodigalgal.ircs.ops;

import com.prodigalgal.ircs.contracts.queue.QueueTopic;

record QueueTopicDescriptor(
        QueueTopic topic,
        String displayName,
        String group,
        String color
) {
    static QueueTopicDescriptor from(QueueTopic topic) {
        return switch (topic) {
            case INGEST_VIDEO -> descriptor(topic, "视频入库", "Ingestion", "#2563eb");
            case PLAYLIST_SYNC -> descriptor(topic, "播放列表同步", "Ingestion", "#0891b2");
            case DOWNLOAD_IMAGE -> descriptor(topic, "图片下载", "Storage", "#4f46e5");
            case PROMOTE_COVER_R2 -> descriptor(topic, "封面 R2 推送", "Storage", "#4338ca");
            case SYNC_AVATAR -> descriptor(topic, "头像同步", "Storage", "#9333ea");
            case CMD_IMAGE_UNLINK -> descriptor(topic, "图片解绑", "Storage", "#475569");
            case EVENT_IMAGE_UNLINKED -> descriptor(topic, "图片删除", "Storage", "#64748b");
            case SEND_MAIL -> descriptor(topic, "邮件通知", "Notification", "#ca8a04");
            case WATCH_PROGRESS -> descriptor(topic, "观看进度", "Interaction", "#0284c7");
            case SEARCH_SYNC_RAW -> descriptor(topic, "Raw 搜索同步", "Search", "#059669");
            case SEARCH_SYNC_UNIFIED -> descriptor(topic, "Unified 搜索同步", "Search", "#65a30d");
            case TASK_PAGE -> descriptor(topic, "采集 Page Task", "Task", "#0f766e");
            case TASK_DETAIL -> descriptor(topic, "采集 Detail Task", "Task", "#2563eb");
            case TASK_PAGE_DISCOVERED -> descriptor(topic, "Page 发现事件", "Task", "#16a34a");
            case TASK_PAGE_FAILED -> descriptor(topic, "Page 失败事件", "Task", "#dc2626");
            case TASK_DETAIL_DONE -> descriptor(topic, "Detail 完成事件", "Task", "#22c55e");
            case TASK_MASTER_DONE -> descriptor(topic, "Master 完成事件", "Task", "#15803d");
        };
    }

    private static QueueTopicDescriptor descriptor(QueueTopic topic, String displayName, String group, String color) {
        return new QueueTopicDescriptor(topic, displayName, group, color);
    }
}
