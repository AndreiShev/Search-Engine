# Search Engine
![Java](https://img.shields.io/badge/-Java-0a0a0a?style=for-the-badge&logo=Java) ![Spring](https://img.shields.io/badge/-Spring-0a0a0a?style=for-the-badge&logo=Spring)
<br/>

>The project was created for educational purposes

## Table of contents
* [General info](#General info)
* [Technologies](#Technologies)
* [Status](#status)

## General info
The project includes 3 services:
* Statistics Service, responsible for uploading statistics to the dashboard;
* Indexing Service that performs indexing of sites specified in `application.yaml`;
* SearchService, which searches for phrases or individual words on sites.

After launching the project, you need to click on the link in the browser `http://localhost:8080 /`. After that, the project dashboard opens, displaying statistics of indexed sites.
<br/>
![dashboard](https://gitlab.skillbox.ru/andrei_sheveliov/java_basics/-/blob/master/SearchEngine/src/main/resources/static/assets/img/readme/dashboard.png)
<br/>

On the management tab, you can start indexing all sites or a separate page.
<br/>
![management](https://gitlab.skillbox.ru/andrei_sheveliov/java_basics/-/blob/master/SearchEngine/src/main/resources/static/assets/img/readme/management.png)
<br/>

On the search tab, you can find pages containing interesting words or phrases.
<br/>
![search](https://gitlab.skillbox.ru/andrei_sheveliov/java_basics/-/blob/master/SearchEngine/src/main/resources/static/assets/img/readme/search.png)
<br/>

## Technologies
* Java - version 17.0.2
* Spring Boot - version 2.7.0

## Status
Project is: _finished_