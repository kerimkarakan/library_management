package com.example.library.api;

import com.example.library.integration.AbstractIntegrationTest;
import com.example.library.model.*;
import com.example.library.repository.BookRepository;
import com.example.library.repository.BorrowRecordRepository;
import com.example.library.repository.MemberRepository;
import com.example.library.dto.BorrowRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * API TEST (End-to-End)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LibraryApiIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BorrowRecordRepository borrowRecordRepository;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api";
        borrowRecordRepository.deleteAll();
        bookRepository.deleteAll();
        memberRepository.deleteAll();
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private Book createTestBook(String isbn, String title, String author) {
        Book book = new Book(isbn, title, author, 3, Genre.TECHNOLOGY);
        return bookRepository.save(book);
    }

    private Member createTestMember(String name, String email, MembershipType type) {
        Member member = new Member(name, email, type);
        return memberRepository.save(member);
    }

    // =========================================================================
    // EXAMPLE: Book API tests — filled in
    // =========================================================================

    @Nested
    @DisplayName("POST /api/books")
    class CreateBookApi {

        @Test
        @DisplayName("should create a book and return 201")
        void shouldCreateBook() {
            Book newBook = new Book("978-0-13-468599-1", "Clean Code", "Robert C. Martin", 3, Genre.TECHNOLOGY);

            ResponseEntity<Book> response = restTemplate.postForEntity(
                    baseUrl + "/books", newBook, Book.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isNotNull();
            assertThat(response.getBody().getTitle()).isEqualTo("Clean Code");
            assertThat(response.getBody().getAvailableCopies()).isEqualTo(3);
        }

        @Test
        @DisplayName("should return 400 when required fields are missing")
        void shouldReturn400_WhenFieldsMissing() {
            Book invalidBook = new Book(); // no required fields set

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/books", invalidBook, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 400 when duplicate ISBN")
        void shouldReturn400_WhenDuplicateIsbn() {
            createTestBook("978-0-13-468599-1", "Clean Code", "Robert C. Martin");

            Book duplicate = new Book("978-0-13-468599-1", "Another Book", "Another Author", 2, Genre.FICTION);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/books", duplicate, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("GET /api/books")
    class GetBooksApi {

        @Test
        @DisplayName("should return all books")
        void shouldReturnAllBooks() {
            createTestBook("978-1", "Book A", "Author A");
            createTestBook("978-2", "Book B", "Author B");

            ResponseEntity<Book[]> response = restTemplate.getForEntity(
                    baseUrl + "/books", Book[].class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(2);
        }

        @Test
        @DisplayName("should return 404 for non-existent book")
        void shouldReturn404_WhenBookNotFound() {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    baseUrl + "/books/999", Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // =========================================================================
    // EXAMPLE: Borrow flow — the most important E2E test
    // =========================================================================

    @Nested
    @DisplayName("Borrow Flow (POST /api/borrows)")
    class BorrowFlowApi {

        @Test
        @DisplayName("should complete full borrow-return cycle")
        void shouldCompleteBorrowReturnCycle() {
            // Setup
            Book book = createTestBook("978-1", "Test Book", "Test Author");
            Member member = createTestMember("Alice", "alice@test.com", MembershipType.STANDARD);

            // 1. Borrow the book
            BorrowRequest borrowRequest = new BorrowRequest(book.getId(), member.getId());
            ResponseEntity<Map> borrowResponse = restTemplate.postForEntity(
                    baseUrl + "/borrows", borrowRequest, Map.class);

            assertThat(borrowResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(borrowResponse.getBody()).containsEntry("bookTitle", "Test Book");
            assertThat(borrowResponse.getBody()).containsEntry("memberName", "Alice");
            assertThat(borrowResponse.getBody()).containsEntry("status", "BORROWED");

            Number borrowId = (Number) borrowResponse.getBody().get("id");

            // 2. Verify book availability decreased
            ResponseEntity<Book> bookResponse = restTemplate.getForEntity(
                    baseUrl + "/books/" + book.getId(), Book.class);
            assertThat(bookResponse.getBody().getAvailableCopies()).isEqualTo(2);

            // 3. Return the book
            ResponseEntity<Map> returnResponse = restTemplate.postForEntity(
                    baseUrl + "/borrows/" + borrowId.longValue() + "/return",
                    null, Map.class);

            assertThat(returnResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(returnResponse.getBody()).containsEntry("status", "RETURNED");

            // 4. Verify book availability increased back
            bookResponse = restTemplate.getForEntity(
                    baseUrl + "/books/" + book.getId(), Book.class);
            assertThat(bookResponse.getBody().getAvailableCopies()).isEqualTo(3);
        }
    }

    // =========================================================================
    // TODO: Students should write these API tests
    // =========================================================================

    @Nested
    @DisplayName("POST /api/borrows - Error cases")
    class BorrowErrorsApi {

        @Test
        @DisplayName("should return 409 when borrowing limit exceeded")
        void shouldReturn409_WhenBorrowLimitExceeded() {
            Member member = createTestMember("Elena", "elena@test.com", MembershipType.STUDENT);

            Book book1 = createTestBook("960-1", "Test Book 1", "Test Author1");
            Book book2 = createTestBook("962-1", "Test Book 2", "Test Author2");
            Book book3 = createTestBook("964-1", "Test Book 3", "Test Author3");

            BorrowRequest borrowRequest1 = new BorrowRequest(book1.getId(), member.getId());
            BorrowRequest borrowRequest2 = new BorrowRequest(book2.getId(), member.getId());

            restTemplate.postForEntity(baseUrl +"/borrows" , borrowRequest1, Map.class);
            restTemplate.postForEntity(baseUrl + "/borrows", borrowRequest2, Map.class);

            BorrowRequest borrowRequest3 = new BorrowRequest(book3.getId(), member.getId());

            ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl + "/borrows", borrowRequest3, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

            assertThat(response.getBody()).isNotNull();
        }

        @Test
        @DisplayName("should return 409 when no copies available")
        void shouldReturn409_WhenNoCopiesAvailable() {
            Book uniqueBook = bookRepository.save(new Book("999-9", "Only One Copy", "Author XYZ", 1, Genre.TECHNOLOGY));

            Member member1 = createTestMember("Mem1","mem1@test.com",MembershipType.STANDARD);
            Member member2 = createTestMember("Mem2","mem2@test.com",MembershipType.STANDARD);

            BorrowRequest borrowRequest1 = new BorrowRequest(uniqueBook.getId(), member1.getId());
            restTemplate.postForEntity(baseUrl+"/borrows", borrowRequest1 , Map.class);

            BorrowRequest borrowRequest2 = new BorrowRequest(uniqueBook.getId(), member2.getId());

            ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl+"/borrows", borrowRequest2,Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("should return 404 when member does not exist")
        void shouldReturn404_WhenMemberNotFound() {
            Book book = createTestBook("911-7" , "Just Book", "Random Author");

            long nonExistentMemberId = 999999;

            BorrowRequest borrowRequest = new BorrowRequest(book.getId(), nonExistentMemberId);

            ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl + "/borrows" , borrowRequest , Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return 404 when book does not exist")
        void shouldReturn404_WhenBookNotFound() {
            Member member = createTestMember("Alex", "alex@test.com", MembershipType.STANDARD);

            long nonExistentBookId = 9898989;

            BorrowRequest borrowRequest = new BorrowRequest(nonExistentBookId, member.getId());

            ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl + "/borrows" , borrowRequest , Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("Member API")
    class MemberApiTests {

        @Test
        @DisplayName("should create a member and return 201")
        void shouldCreateMember() {
            Member newMember = new Member("Michael Johnson", "michael.johnson@example.com", MembershipType.STANDARD);

            ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl + "/members", newMember, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).containsEntry("name", "Michael Johnson");
            assertThat(response.getBody()).containsEntry("email", "michael.johnson@example.com");
            assertThat(response.getBody()).containsEntry("membershipType", "STANDARD");
            assertThat(response.getBody()).containsEntry("active", true);
            assertThat(response.getBody().get("id")).isNotNull();
        }

        @Test
        @DisplayName("should deactivate a member via DELETE")
        void shouldDeactivateMember() {
            Member member = createTestMember("Daniel Davis", "daniel.davis@test.com", MembershipType.PREMIUM);

            ResponseEntity<Void> deleteResponse = restTemplate.exchange(baseUrl + "/members/" + member.getId(), HttpMethod.DELETE, null, Void.class);

            assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            ResponseEntity<Map> response = restTemplate.getForEntity(baseUrl + "/members/" + member.getId(), Map.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).containsEntry("active", false);
        }

        @Test
        @DisplayName("should return 400 when creating member with invalid email")
        void shouldReturn400_WhenInvalidEmail() {
            Member invalidMember = new Member("Bad Email Bob", "this-is-not-an-email", MembershipType.STANDARD);

            ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl + "/members", invalidMember, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).containsKey("errors");

            Map<String, String> errors = (Map<String, String>) response.getBody().get("errors");
            assertThat(errors).containsEntry("email", "Valid email is required");
        }
    }

    @Nested
    @DisplayName("Search & Filter API")
    class SearchApiTests {

        @Test
        @DisplayName("should search books by keyword via GET /api/books/search?keyword=...")
        void shouldSearchBooks() {
            createTestBook("978-100", "Learning Spring Boot", "Greg L. Turnquist");
            createTestBook("978-200", "Clean Code", "Robert C. Martin");
            createTestBook("978-300", "Spring Microservices in Action", "John Carnell");

            ResponseEntity<List> response = restTemplate.getForEntity(baseUrl + "/books/search?keyword=Spring", List.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).hasSize(2);

            List<Map<String, Object>> returnedBooks = (List<Map<String, Object>>) response.getBody();
            assertThat(returnedBooks).extracting(bookMap -> bookMap.get("title")).containsExactlyInAnyOrder("Learning Spring Boot", "Spring Microservices in Action");
        }

        @Test
        @DisplayName("should get active borrows for a member")
        void shouldGetActiveBorrows() {
            Member member = createTestMember("Charlie", "charlie@example.com", MembershipType.STANDARD);
            Book book1 = createTestBook("978-10", "First Book", "Author One");
            Book book2 = createTestBook("978-20", "Second Book", "Author Two");

            BorrowRequest borrowRequest1 = new BorrowRequest(book1.getId(), member.getId());
            ResponseEntity<Map> borrow1Response = restTemplate.postForEntity(baseUrl + "/borrows", borrowRequest1, Map.class);
            assertThat(borrow1Response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            BorrowRequest borrowRequest2 = new BorrowRequest(book2.getId(), member.getId());
            ResponseEntity<Map> borrow2Response = restTemplate.postForEntity(baseUrl + "/borrows", borrowRequest2, Map.class);
            assertThat(borrow2Response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(borrow2Response.getBody()).isNotNull();

            Number borrow2Id = (Number) borrow2Response.getBody().get("id");

            ResponseEntity<Map> returnResponse = restTemplate.postForEntity(baseUrl + "/borrows/" + borrow2Id.longValue() + "/return", null, Map.class);
            assertThat(returnResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            ResponseEntity<List> response = restTemplate.getForEntity(baseUrl + "/borrows/member/" + member.getId() + "/active", List.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).hasSize(1);

            List<Map<String, Object>> activeBorrows = (List<Map<String, Object>>) response.getBody();
            assertThat(activeBorrows.get(0)).containsEntry("bookTitle", "First Book");
        }
    }
}
