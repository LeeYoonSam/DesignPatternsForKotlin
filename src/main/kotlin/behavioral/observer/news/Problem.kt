package behavioral.observer.news

class Problem {
    /**
     * 문제점
     * - 객체 간 강한 결합
     * - 새로운 구독자 추가 시 발행자 코드 수정 필요
     * - 확장성과 유연성 부족
     */
    class NewsPublisher {
        private var latestNews: String = ""

        fun publishNews(news: String) {
            latestNews = news

            // 각 구독자에게 직접 알림을 보내는 비효율적인 방식
            User1.update(latestNews)
            User2.update(latestNews)
            User3.update(latestNews)
        }
    }

    class User1 {
        companion object {
            fun update(news: String) {
                println("User1 received news: $news")
            }
        }
    }

    class User2 {
        companion object {
            fun update(news: String) {
                println("User2 received news: $news")
            }
        }
    }

    class User3 {
        companion object {
            fun update(news: String) {
                println("User3 received news: $news")
            }
        }
    }
}

fun main() {
    val newsPublisher = Problem.NewsPublisher()
    newsPublisher.publishNews("It is a new information.")
}