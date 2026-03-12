# uilchilgeend-suurilsan-architecture

Төслийн нэр:

Service Oriented Architecture хэрэглэгчийн систем

Төслийн танилцуулга:

Энэхүү төсөл нь Service Oriented Architecture (SOA) ашиглан хэрэглэгчийн удирдлагын системийг хэрэгжүүлсэн.

Систем нь дараах боломжуудтай:

-Хэрэглэгч бүртгүүлэх

-Нэвтрэх

-Profile удирдах

-Token authentication

Системийн бүрэлдэхүүн:

Систем дараах хэсгүүдээс бүрдэнэ.

Frontend
HTML + JavaScript

JSON Service
Spring Boot REST API

SOAP Service
Spring Web Services ашигласан authentication service

Database
MySQL


Database:

Database нэр

soa_lab

Жишээ table

CREATE TABLE users (
 id INT AUTO_INCREMENT PRIMARY KEY,
 username VARCHAR(50),
 password VARCHAR(100),
 email VARCHAR(100),
 token VARCHAR(255)
);
Суулгах заавар
1 Database үүсгэх
CREATE DATABASE soa_lab;
2 application.properties тохируулах
spring.datasource.url=jdbc:mysql://localhost:3306/soa_lab
spring.datasource.username=root
spring.datasource.password=1234

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
3 SOAP Service ажиллуулах
user-soap-service

port

8080
4 JSON Service ажиллуулах
user-json-service

port

8081
5 Frontend нээх
index.html

Browser дээр нээнэ.

Архитектурын шийдвэр:

Системд дараах шийдвэрүүдийг гаргасан.

Frontend нь REST API ашиглан JSON service-тэй холбогдоно

Authentication SOAP service ашиглан хийгдэнэ

MySQL database ашигласан

Token-based authentication хэрэгжүүлсэн

Database сонгосон шалтгаан

MySQL database сонгосон шалтгаан:

Relational database бүтэцтэй

Spring Boot-тэй сайн ажилладаг

Өргөн хэрэглэгддэг

JPA / Hibernate-тэй хялбар холбогддог
