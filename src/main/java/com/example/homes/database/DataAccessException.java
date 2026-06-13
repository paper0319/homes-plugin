package com.example.homes.database;

/** DB への書き込みに失敗したときに HomeRepository 実装が投げる例外。 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
