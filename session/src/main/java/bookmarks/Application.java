package bookmarks;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.ResourceSupport;
import org.springframework.hateoas.Resources;
import org.springframework.hateoas.VndErrors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configurers.GlobalAuthenticationConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.HeaderHttpSessionStrategy;
import org.springframework.session.web.http.HttpSessionStrategy;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

//
// curl -X POST -vu android-bookmarks:123456 http://localhost:8080/oauth/token -H "Accept: application/json" -d "password=password&username=jlong&grant_type=password&scope=write&client_secret=123456&client_id=android-bookmarks"
// curl -v POST http://127.0.0.1:8080/tags --data "tags=cows,dogs"  -H "Authorization: Bearer 66953496-fc5b-44d0-9210-b0521863ffcb"

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

@Configuration
class WebSecurityConfiguration extends GlobalAuthenticationConfigurerAdapter {

    @Autowired
    AccountRepository accountRepository;

    @Override
    public void init(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userDetailsService());
    }

    @Bean
    UserDetailsService userDetailsService() {
        return (username) -> accountRepository
                .findByUsername(username)
                .map(a -> new User(a.username, a.password, true, true, true, true,
                        AuthorityUtils.createAuthorityList("USER", "write")))
                .orElseThrow(
                        () -> new UsernameNotFoundException("could not find the user '"
                                + username + "'"));
    }
}

@Configuration
@EnableWebSecurity
class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests()
                .anyRequest().authenticated()
                .and().httpBasic();
    }
}

@EnableRedisHttpSession
class HttpSessionConfig {
    @Bean
    HttpSessionStrategy httpSessionStrategy() {
        return new HeaderHttpSessionStrategy();
    }
}

@RestController
class AuthController {
    @RequestMapping
    String check(Principal principal) {
        return principal.getName();
    }
}

class BookmarkResource extends ResourceSupport {

    private final Bookmark bookmark;

    public BookmarkResource(Bookmark bookmark) {
        String username = bookmark.getAccount().getUsername();
        this.bookmark = bookmark;
        this.add(new Link(bookmark.uri, "bookmark-uri"));
        this.add(linkTo(BookmarkRestController.class, username).withRel("bookmarks"));
        this.add(linkTo(
                methodOn(BookmarkRestController.class, username).readBookmark(null,
                        bookmark.getId())).withSelfRel());
    }

    public Bookmark getBookmark() {
        return bookmark;
    }
}

@RestController
@RequestMapping("/bookmarks")
class BookmarkRestController {

    @Autowired
    BookmarkRepository bookmarkRepository;

    @Autowired
    AccountRepository accountRepository;

    @RequestMapping(method = RequestMethod.POST)
    ResponseEntity<?> add(Principal principal, @RequestBody Bookmark in) {
        String userId = principal.getName();
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
    BookmarkResource readBookmark(Principal principal, @PathVariable Long bookmarkId) {
        String userId = principal.getName();
        this.validateUser(userId);
        return new BookmarkResource(this.bookmarkRepository.findOne(bookmarkId));
    }

    @RequestMapping(method = RequestMethod.GET)
    Resources<BookmarkResource> readBookmarks(Principal principal) {
        String userId = principal.getName();
        this.validateUser(userId);

        List<BookmarkResource> bookmarkResourceList = bookmarkRepository
                .findByAccountUsername(userId).stream().map(BookmarkResource::new)
                .collect(Collectors.toList());
        return new Resources<>(bookmarkResourceList);
    }

    private void validateUser(String userId) {
        this.accountRepository.findByUsername(userId).orElseThrow(
                () -> new UserNotFoundException(userId));
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

@SuppressWarnings("serial")
class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String userId) {
        super("could not find user '" + userId + "'.");
    }
}
