package com.blog.blog_literario.exception;

/** Thrown when a requested entity cannot be found; maps to HTTP 404. */
public class ResourceNotFoundException extends RuntimeException{
    public ResourceNotFoundException(String mensaje){
        super(mensaje);
    }
}
