import React from "react";
import { MovieDetail } from "@/lib/types";
import {
    generateBreadcrumbSchema,
    generateMovieSchema
} from "@/lib/seo";

interface MovieStructuredDataProps {
    movie: MovieDetail;
}

export function MovieStructuredData({ movie }: MovieStructuredDataProps) {
    const movieSchema = generateMovieSchema(movie);

    const breadcrumbItems = [
        { name: "首页", url: "/" },
        {
            name: movie.categoryName || "探索",
            url: `/explore?type=${movie.categoryName === '剧集' ? 'series' : 'movie'}`
        }
    ];

    if (movie.genres && movie.genres.length > 0) {
        breadcrumbItems.push({
            name: movie.genres[0],
            url: `/explore?genre=${encodeURIComponent(movie.genres[0])}`
        });
    }

    breadcrumbItems.push({ name: movie.title, url: `/movie/${movie.id}` });

    const breadcrumbSchema = generateBreadcrumbSchema(breadcrumbItems);

    return (
        <>
            <script
                type="application/ld+json"
                dangerouslySetInnerHTML={{ __html: JSON.stringify(movieSchema) }}
            />
            <script
                type="application/ld+json"
                dangerouslySetInnerHTML={{ __html: JSON.stringify(breadcrumbSchema) }}
            />
        </>
    );
}
