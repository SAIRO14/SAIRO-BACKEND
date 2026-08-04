package com.sairo.sairo_backend.analysis;

import com.sairo.sairo_backend.course.SpotSummary;

import java.util.List;

public record TasteAnalysisResponse(
        List<String> moodTags,
        String summary,
        List<CourseCard> courses
) {
    public record CourseCard(
            String courseId,
            String regionName,
            String regionArea,
            String imageUrl,
            String reason,
            boolean saved,
            List<SpotSummary> day1,
            List<SpotSummary> day2
    ) {}
}
