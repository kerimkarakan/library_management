package com.example.library.integration;

import com.example.library.model.Book;
import com.example.library.model.Genre;
import com.example.library.repository.BookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * INTEGRATION TEST - Repository Layer
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BookRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private BookRepository bookRepository;

    @BeforeEach
    void setUp() {
        bookRepository.deleteAll();
    }

    private Book createBook(String isbn, String title, String author, int copies, Genre genre) {
        Book book = new Book(isbn, title, author, copies, genre);
        book.setPublishedDate(LocalDate.of(2020, 1, 1));
        return bookRepository.save(book);
    }

    // =========================================================================
    // EXAMPLE: Basic CRUD and custom query tests — filled in
    // =========================================================================

    @Nested
    @DisplayName("Basic CRUD operations")
    class CrudTests {

        @Test
        @DisplayName("should save and retrieve a book by ID")
        void shouldSaveAndFindById() {
            Book saved = createBook("978-0-13-468599-1", "Clean Code", "Robert C. Martin", 3, Genre.TECHNOLOGY);

            Optional<Book> found = bookRepository.findById(saved.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getTitle()).isEqualTo("Clean Code");
            assertThat(found.get().getIsbn()).isEqualTo("978-0-13-468599-1");
        }

        @Test
        @DisplayName("should find book by ISBN")
        void shouldFindByIsbn() {
            createBook("978-0-13-468599-1", "Clean Code", "Robert C. Martin", 3, Genre.TECHNOLOGY);

            Optional<Book> found = bookRepository.findByIsbn("978-0-13-468599-1");

            assertThat(found).isPresent();
            assertThat(found.get().getTitle()).isEqualTo("Clean Code");
        }

        @Test
        @DisplayName("should return empty when ISBN not found")
        void shouldReturnEmpty_WhenIsbnNotFound() {
            Optional<Book> found = bookRepository.findByIsbn("non-existent");

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("Custom query methods")
    class CustomQueryTests {

        @Test
        @DisplayName("should search books by keyword in title or author (case insensitive)")
        void shouldSearchByKeyword() {
            createBook("978-1", "Clean Code", "Robert C. Martin", 3, Genre.TECHNOLOGY);
            createBook("978-2", "Clean Architecture", "Robert C. Martin", 2, Genre.TECHNOLOGY);
            createBook("978-3", "Design Patterns", "Gang of Four", 5, Genre.TECHNOLOGY);

            List<Book> results = bookRepository.searchBooks("clean");

            assertThat(results).hasSize(2);
            assertThat(results).extracting(Book::getTitle)
                    .containsExactlyInAnyOrder("Clean Code", "Clean Architecture");
        }

        @Test
        @DisplayName("should find available books (copies > 0)")
        void shouldFindAvailableBooks() {
            Book available = createBook("978-1", "Available Book", "Author A", 3, Genre.FICTION);
            Book unavailable = createBook("978-2", "Unavailable Book", "Author B", 1, Genre.FICTION);
            unavailable.setAvailableCopies(0);
            bookRepository.save(unavailable);

            List<Book> results = bookRepository.findAvailableBooks();

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getTitle()).isEqualTo("Available Book");
        }
    }

    // =========================================================================
    // TODO: Students should write these integration tests
    // =========================================================================

    @Nested
    @DisplayName("Genre and author queries")
    class FilterTests {

        @Test
        @DisplayName("should find books by genre")
        void shouldFindByGenre() {
            createBook("111-1", "Cosmos", "Carl Sagan", 2, Genre.SCIENCE);
            createBook("111-2", "A Brief History of Time", "Stephen Hawking", 3, Genre.SCIENCE);
            createBook("111-3", "Dune", "Frank Herbert", 5, Genre.FICTION);

            List<Book> results = bookRepository.findByGenre(Genre.SCIENCE);

            assertThat(results).hasSize(2);
            assertThat(results).extracting(Book::getGenre).containsOnly(Genre.SCIENCE);
        }

        @Test
        @DisplayName("should find books by author (case insensitive, partial match)")
        void shouldFindByAuthor() {
            createBook("222-1", "The Hobbit", "J.R.R. Tolkien", 2, Genre.FICTION);
            createBook("222-2", "The Silmarillion", "Christopher Tolkien", 3, Genre.FICTION);
            createBook("222-3", "1984", "George Orwell", 5, Genre.FICTION);

            List<Book> results = bookRepository.findByAuthorContainingIgnoreCase("tolkien");

            assertThat(results).hasSize(2);
            assertThat(results).extracting(Book::getAuthor).containsExactlyInAnyOrder("J.R.R. Tolkien", "Christopher Tolkien");
        }

        @Test
        @DisplayName("should search by author name using searchBooks()")
        void shouldSearchByAuthorKeyword() {
            createBook("333-1", "It", "Stephen King", 2, Genre.FICTION);
            createBook("333-2", "The Universe in a Nutshell", "Stephen Hawking", 3, Genre.SCIENCE);

            List<Book> results = bookRepository.searchBooks("stephen");

            assertThat(results).hasSize(2);
            assertThat(results).extracting(Book::getAuthor).containsExactlyInAnyOrder("Stephen King", "Stephen Hawking");
        }

        @Test
        @DisplayName("should return empty list when no books match search")
        void shouldReturnEmpty_WhenNoMatch() {
            createBook("444-1", "Clean Code", "Robert C. Martin", 2, Genre.TECHNOLOGY);

            List<Book> results = bookRepository.searchBooks("nonexistentkeyword");

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should enforce unique ISBN constraint")
        void shouldEnforceUniqueIsbn() {
            String duplicateIsbn = "978-0-123456-47-2";
            createBook(duplicateIsbn, "First Book", "Author A", 2, Genre.FICTION);

            Book secondBook = new Book(duplicateIsbn, "Second Book", "Author B", 3, Genre.SCIENCE);
            secondBook.setPublishedDate(LocalDate.of(2021, 1, 1));

            assertThrows(DataIntegrityViolationException.class, () -> {
                bookRepository.save(secondBook);
                bookRepository.flush();
            });
        }

        @Test
        @DisplayName("should handle deleting a book")
        void shouldDeleteBook() {
            Book saved = createBook("555-1", "Book to Delete", "Author A", 2, Genre.FICTION);
            Long bookId = saved.getId();

            bookRepository.deleteById(bookId);

            Optional<Book> found = bookRepository.findById(bookId);
            assertThat(found).isEmpty();
        }
    }
}
