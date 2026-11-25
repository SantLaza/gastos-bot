package com.santlaza.gastosbot.gasto;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "gastos")
public class Gasto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long chatId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    private String descripcion;

    @Column(nullable = false)
    private String categoria;

    @Column(nullable = false)
    private LocalDate fecha;

    protected Gasto() {} // JPA lo necesita

    public Gasto(Long chatId, BigDecimal monto, String descripcion, String categoria, LocalDate fecha) {
        this.chatId = chatId;
        this.monto = monto;
        this.descripcion = descripcion;
        this.categoria = categoria;
        this.fecha = fecha;
    }

    public Long getId() { return id; }
    public Long getChatId() { return chatId; }
    public BigDecimal getMonto() { return monto; }
    public String getDescripcion() { return descripcion; }
    public String getCategoria() { return categoria; }
    public LocalDate getFecha() { return fecha; }
}
