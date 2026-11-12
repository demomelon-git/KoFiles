package com.example.kofiles;

interface IFileService {
    // Метод, который требует Shizuku для остановки службы
    void destroy() = 16777114;

    // Единственная точка входа: ответ = [0] + данные  или  [1] + текст ошибки
    byte[] call(String method, in String[] args) = 1;
}
