package com.prodigalgal.ircs.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class CatalogCategoryMappingPolicy {

    private static final List<MappingRule> RULES = new ArrayList<>();

    static {
        RULES.add(new MappingRule("短剧", 100,
                Set.of("短剧", "微短剧", "爽剧", "爽文", "逆袭", "女频", "男频",
                        "重生", "赘婿", "脑洞", "闪婚", "都市", "总裁", "言情",
                        "穿越", "年代", "复仇", "战神", "神医", "萌宝", "甜宠", "虐恋",
                        "古装", "仙侠", "悬疑"),
                Set.of("预告", "花絮", "资讯", "解说", "电影", "片", "电视剧", "连续剧", "剧集", "动漫", "动画")));

        RULES.add(new MappingRule("动漫", 95,
                Set.of("动漫", "动画", "剧场版", "里番", "有声动漫"),
                Set.of("预告", "花絮", "资讯", "解说")));

        RULES.add(new MappingRule("体育", 95,
                Set.of("体育", "赛事", "篮球", "足球", "NBA", "CBA", "网球",
                        "斯诺克", "LPL", "电竞", "赛车", "奥运"),
                Set.of("资讯", "新闻")));

        RULES.add(new MappingRule("纪录片", 95,
                Set.of("纪录", "记录"),
                Set.of("资讯")));

        RULES.add(new MappingRule("综艺", 90,
                Set.of("综艺", "真人秀", "晚会", "演唱会", "访谈"),
                Set.of("资讯", "预告")));

        RULES.add(new MappingRule("电视剧", 80,
                Set.of("电视剧", "连续剧", "剧集", "Netflix",
                        "国产剧", "内地剧", "大陆剧", "国剧",
                        "港剧", "香港剧", "港澳剧", "台剧", "台湾剧",
                        "韩剧", "韩国剧", "日剧", "日本剧",
                        "美剧", "欧美剧", "海外剧", "泰剧", "马泰剧",
                        "网剧", "自制剧"),
                Set.of("动画", "动漫", "短剧", "预告", "花絮", "资讯", "解说", "电影")));

        RULES.add(new MappingRule("电影", 70,
                Set.of("电影", "片", "大片", "微电影", "短片",
                        "动作", "喜剧", "爱情", "科幻", "恐怖", "剧情", "战争",
                        "犯罪", "灾难", "悬疑", "惊悚", "奇幻", "冒险", "历史",
                        "古装", "家庭", "西部", "伦理", "福利", "写真", "邵氏"),
                Set.of("电视剧", "连续剧", "剧集", "短剧", "动画", "动漫",
                        "预告", "花絮", "资讯", "解说", "新闻")));

        RULES.add(new MappingRule("其他", 60,
                Set.of("预告", "花絮", "解说", "资讯", "新闻", "头条",
                        "公告", "搞笑", "音乐", "MV", "教学", "课堂", "学习", "未分类"),
                null));
    }

    String inferCategoryName(String sourceName) {
        if (!StringUtils.hasText(sourceName)) {
            return null;
        }
        String cleanName = sourceName.trim();
        String bestMatchName = null;
        int maxScore = 0;

        for (MappingRule rule : RULES) {
            int score = rule.calculateScore(cleanName);
            if (score > maxScore) {
                maxScore = score;
                bestMatchName = rule.targetCategory();
            }
        }
        return bestMatchName;
    }

    private record MappingRule(String targetCategory, int baseWeight, Set<String> keywords, Set<String> negativeKeywords) {

        private int calculateScore(String input) {
            if (negativeKeywords != null) {
                for (String negative : negativeKeywords) {
                    if (input.contains(negative)) {
                        return -1;
                    }
                }
            }

            for (String keyword : keywords) {
                if (input.contains(keyword)) {
                    return input.equals(keyword) ? baseWeight + 50 : baseWeight;
                }
            }
            return 0;
        }
    }
}
