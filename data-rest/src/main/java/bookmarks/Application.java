package bookmarks;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.stream.Stream;

@SpringBootApplication
public class Application {
    @Bean
    CommandLineRunner init(AccountRepository accountRepository,
                           BookmarkRepository bookmarkRepository) {
        return (evt) -> Stream.of("kis", "skrb", "making")
                .forEach(a -> {
                    Account account = accountRepository.save(new Account(a,
                            "password"));
                    bookmarkRepository.save(new Bookmark(account,
                            "http://bookmark.com/1/" + a, "A description"));
                    bookmarkRepository.save(new Bookmark(account,
                            "http://bookmark.com/2/" + a, "A description"));
                });
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
