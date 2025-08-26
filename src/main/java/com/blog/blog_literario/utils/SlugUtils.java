package com.blog.blog_literario.utils;

import java.text.Normalizer;

public class SlugUtils {

    //Método para crear Slugs automaticamente
    public static String toSlug(String input){
        //Separa las marcar diacriticas(acentos) en caracteres separados
        //CANCIÓN => C a n c i o ´ n 
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                            .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        
        return normalized
                .toLowerCase() //convierte a minusculas
                //reemplaza todo lo que no sea minusculas, espacios, numeros o guiones por un espacio
                .replaceAll("[^a-z0-9\\s-]", "")
                //si hay dos o mas espacios los reemplaza por un guion (-)
                .replaceAll("\\s+", "-")
                //si hay dos o mas guiones(--+) los reemplaza por uno (-)
                .replaceAll("-{2}","-")
                //si hay un guion al inicio o final ("-hola mundo-") los quita ("hola mundo")
                .replaceAll("^-|-$","");
    }
}
