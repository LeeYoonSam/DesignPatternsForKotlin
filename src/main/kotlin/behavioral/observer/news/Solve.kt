package behavioral.observer.news

class Solve {
    interface NewsSubscriber {
        fun update(news: String)
    }

    // 뉴스 발행자 (주체)
    class NewsPublisherObserver {
        private val subscribers = mutableListOf<NewsSubscriber>()

        fun addSubscriber(subscriber: NewsSubscriber) {
            subscribers.add(subscriber)
        }

        fun removeSubscriber(subscriber: NewsSubscriber) {
            subscribers.remove(subscriber)
        }

        fun publishNews(news: String) {
            // 모든 구독자에게 자동으로 알림
            subscribers.forEach { it.update(news) }
        }
    }

    class User1Observer(private val name: String): NewsSubscriber {
        override fun update(news: String) {
            println("$name received news: $news")
        }
    }

    class User2Observer(private val name: String): NewsSubscriber {
        override fun update(news: String) {
            println("$name received news: $news")
        }
    }

    class User3Observer(private val name: String): NewsSubscriber {
        override fun update(news: String) {
            println("$name received news: $news")
        }
    }
}

fun main() {
    val newsPublisher = Solve.NewsPublisherObserver()

    val user1 = Solve.User1Observer("Albert")
    val user2 = Solve.User2Observer("John")
    val user3 = Solve.User3Observer("Alice")

    // 구독자 등록
    newsPublisher.addSubscriber(user1)
    newsPublisher.addSubscriber(user2)
    newsPublisher.addSubscriber(user3)

    // 뉴스 발행
    newsPublisher.publishNews("Breaking: Tech Innovation Breakthrough!")
}