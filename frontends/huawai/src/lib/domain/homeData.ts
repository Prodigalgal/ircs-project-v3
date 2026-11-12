import { CategorySection, HomeData, MovieCard } from "@/lib/types";

const DEFAULT_SPOTLIGHT_LIMIT = 5;
const DEFAULT_TRENDING_LIMIT = 12;
const DEFAULT_RAIL_MOVIE_LIMIT = 6;

export interface HomeDataCompactOptions {
    spotlightLimit?: number;
    trendingLimit?: number;
    railMovieLimit?: number;
}

export function compactHomeData(data: HomeData, options: HomeDataCompactOptions = {}): HomeData {
    const spotlightLimit = options.spotlightLimit ?? Number.POSITIVE_INFINITY;
    const trendingLimit = options.trendingLimit ?? Number.POSITIVE_INFINITY;
    const railMovieLimit = options.railMovieLimit ?? Number.POSITIVE_INFINITY;

    return {
        spotlight: data.spotlight.slice(0, spotlightLimit).map(compactSpotlightMovie),
        trending: data.trending.slice(0, trendingLimit).map(compactTrendingMovie),
        sections: data.sections.map(section => compactSection(section, railMovieLimit))
    };
}

export function compactInitialHomeData(data: HomeData): HomeData {
    return compactHomeData(data, {
        spotlightLimit: DEFAULT_SPOTLIGHT_LIMIT,
        trendingLimit: DEFAULT_TRENDING_LIMIT,
        railMovieLimit: DEFAULT_RAIL_MOVIE_LIMIT
    });
}

function compactSection(section: CategorySection, movieLimit: number): CategorySection {
    return {
        id: section.id,
        title: section.title,
        movies: section.movies.slice(0, movieLimit).map(compactRailMovie)
    };
}

function compactSpotlightMovie(movie: MovieCard): MovieCard {
    return {
        ...compactRailMovie(movie),
        description: movie.description,
        genres: movie.genres
    };
}

function compactTrendingMovie(movie: MovieCard): MovieCard {
    return {
        id: movie.id,
        title: movie.title,
        posterUrl: movie.posterUrl,
        rating: movie.rating,
        releaseYear: movie.releaseYear,
        categoryName: movie.categoryName,
        area: movie.area
    };
}

function compactRailMovie(movie: MovieCard): MovieCard {
    return {
        id: movie.id,
        title: movie.title,
        aliasTitle: movie.aliasTitle,
        posterUrl: movie.posterUrl,
        rating: movie.rating,
        releaseYear: movie.releaseYear,
        categoryName: movie.categoryName,
        area: movie.area,
        remarks: movie.remarks,
        season: movie.season,
        subtitle: movie.subtitle
    };
}
