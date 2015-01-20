package bookmarks;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.ResourceSupport;
import org.springframework.hateoas.Resources;
import org.springframework.hateoas.VndErrors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

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

class BookmarkResource extends ResourceSupport {
    private final Bookmark bookmark;

    public BookmarkResource(Bookmark bookmark) {
        String username = bookmark.getAccount().getUsername();
        this.bookmark = bookmark;
        this.add(new Link(bookmark.getUri(), "bookmark-uri"));
        this.add(linkTo(BookmarkRestController.class, username)
                .withRel("bookmarks"));
        this.add(linkTo(methodOn(BookmarkRestController.class, username)
                .readBookmark(username, bookmark.getId()))
                .withSelfRel());
    }

    public Bookmark getBookmark() {
        return bookmark;
    }
}

@RestController
@RequestMapping("/{userId}/bookmarks")
class BookmarkRestController {

    @Autowired
    BookmarkRepository bookmarkRepository;

    @Autowired
    AccountRepository accountRepository;

    @RequestMapping(method = RequestMethod.POST)
    ResponseEntity<?> add(@PathVariable String userId, @RequestBody Bookmark in) {
        return accountRepository
                .findByUsername(userId)
                .map(account -> {
                    Bookmark bookmark = bookmarkRepository
                            .save(new Bookmark(account, in.uri, in.description));
                    Link selfLink = new BookmarkResource(bookmark).getLink("self");
                    URI location = URI.create(selfLink.getHref());
                    return ResponseEntity.created(location).body(bookmark);
                })
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    @RequestMapping(value = "/{bookmarkId}", method = RequestMethod.GET)
    BookmarkResource readBookmark(@PathVariable String userId, @PathVariable Long bookmarkId) {
        this.validateUser(userId);
        return new BookmarkResource(this.bookmarkRepository.findOne(bookmarkId));
    }


    @RequestMapping(method = RequestMethod.GET)
    Resources<BookmarkResource> readBookmarks(@PathVariable String userId) {
        this.validateUser(userId);
        List<BookmarkResource> bookmarkResourceList = bookmarkRepository.findByAccountUsername(userId)
                .stream()
                .map(BookmarkResource::new)
                .collect(Collectors.toList());
        return new Resources<>(bookmarkResourceList);
    }

    private void validateUser(String userId) {
        this.accountRepository.findByUsername(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }
}

@ControllerAdvice
class BookmarkControllerAdvice {

    @ResponseBody
    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    VndErrors userNotFoundExceptionHandler(UserNotFoundException ex) {
        return new VndErrors("error", ex.getMessage());
    }
}


class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String userId) {
        super("could not find user '" + userId + "'.");
    }
}
