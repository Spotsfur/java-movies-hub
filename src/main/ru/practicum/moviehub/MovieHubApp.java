package ru.practicum.moviehub;

import ru.practicum.moviehub.http.MoviesServer;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

public class MovieHubApp {
    public static void main(String[] args) {
        final MoviesStore moviesStore = new MoviesStore();
        moviesStore.addMovie(new Movie("Зверополис 2", 2025));
        moviesStore.addMovie(new Movie("Начало", 2010));
        moviesStore.addMovie(new Movie("Интерстеллар", 2014));
        moviesStore.addMovie(new Movie("Аватар 3", 2025));

        final MoviesServer server = new MoviesServer(moviesStore,8080);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }
}