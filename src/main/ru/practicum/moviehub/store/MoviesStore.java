package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MoviesStore {
    private int movieId;
    private Map<Integer, Movie> movies = new HashMap<>();

    public MoviesStore() {
        this.movieId = 0;
    }

    public void addMovie(Movie movie) {
        movieId++;
        movies.put(movieId, movie);
    }

    public void deleteMovie(int id) {
        movies.remove(id);
    }

    public HashMap<Integer, Movie> getMovies() {
        return new HashMap<>(movies);
    }

    public List<Movie> mapToList (Map<Integer, Movie> mapOfMovies) {
        return mapOfMovies.values().stream().toList();
    }
}