
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.math.pow
import scala.util.Random
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import akka.actor._
import akka.io._
import akka.util.Timeout

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.actorRef2Scala
import spray.client.pipelining._
import spray.http._
import spray.http.HttpCharsets._
import spray.http.HttpMethods._
import spray.http.MediaTypes._
import spray.httpx.SprayJsonSupport._
import spray.httpx.unmarshalling._
import spray.httpx._
import spray.json.DefaultJsonProtocol
import spray.httpx.encoding.{Gzip, Deflate}
import spray.client.pipelining._
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global

sealed trait serviceMessage
case class Start() extends serviceMessage
case class Initiate() extends serviceMessage
case class CreateProfile() extends serviceMessage
case class CreateFriendList() extends serviceMessage
case class StartPosting() extends serviceMessage
case class StartPagePosting() extends serviceMessage
case class StartBrowsing() extends serviceMessage
case class FollowPages() extends serviceMessage
case class CreateUser(userName: String) extends serviceMessage
case class UpdateProfile(detailName: String, detailValue: String) extends serviceMessage
case class GetProfile() extends serviceMessage
case class AddFriend(frndId: Int) extends serviceMessage
case class GetFriendList() extends serviceMessage
case class CreatePost(message: String) extends serviceMessage
case class GetPosts() extends serviceMessage
case class UpdatePost(postId: Int, message: String) extends serviceMessage
case class DeletePost(postId: Int) extends serviceMessage
case class GetPostCount() extends serviceMessage
case class GetPage() extends serviceMessage
case class CreatePage(pageName: String) extends serviceMessage
case class CreatePagePost(pageId: Int, message: String) extends serviceMessage
case class AddPageFollower(userId: Int, pageId: Int) extends serviceMessage
case class GetPageFollowerList(pageId: Int) extends serviceMessage
  	  

object fbookClient extends App {
  if(args.isEmpty || args(0).isEmpty() || args(1).isEmpty()){
		println("Enter the number of users & number of pages to simulate.");
	}else{
		InitializeAndInitiate(args(0).toInt, args(1).toInt)
	}

	def InitializeAndInitiate(totalUsers: Int, totalPages: Int) 
	{
		val system = ActorSystem("FacebookSimulatorSystem")
		val master = system.actorOf(Props(new Master(totalUsers, totalPages)), name = "Master")
		master ! Start
	}
}
	
class Master (totalUsers: Int, totalPages: Int) extends Actor{

	var AllActors = new ArrayBuffer[ActorRef]()
	var numUsers = totalUsers.toInt
	var numPages = totalPages.toInt
	var terminatedNodeCount: Int = 0
	var messageCounter: Int = 0
	var msgPropTime: Long = 0

  println("user to be spawned : "+ totalUsers)    
	for (i <- 0 until numUsers){ 
		var user = context.actorOf(Props(new Worker("Actors" + i)), name = "Actors" + i)
		AllActors += user    // Hash map with user Id and corresponding actor
		println("user Name: Actor"+i+"; has joined")
		user ! Initiate
	}
	
	for (i <- 0 until numPages){ 
		var page = context.actorOf(Props(new Worker("Pages" + i)), name = "Pages" + i)
		AllActors += page    // Hash map with user Id and corresponding actor
		println("Page Name: Pages"+i+"; has joined")
		page ! Initiate
	}
	
	//Initializing stats
	Global.Initialize(numUsers)

	def receive = {
	  case Start => {
	    println("Master started with users :" + totalUsers)
	  }
	}
}

case class createUserRequest(userName: String)
case class getProfileResponse(profile: Map[String,String])
case class friendListResponse(friendList: Map[String,String])
case class updateProfileRequest(detailName: String, detailValue: String)
case class addFriendRequest(userId:Int, friendId: Int)
case class createPostRequest(userId:Int, message: String)
case class updatePostRequest(postId:Int, message: String)
case class deletePostRequest(postId:Int)
case class getPostResponse(posts: Map[String,String])
case class getPageResponse(page: Map[String,String])
case class createPageRequest(pageName: String)
case class createPagePostRequest(pageId: Int, message: String)
case class followerListResponse(followerList: Map[String,String])
case class addPageFollowerRequest(userId: Int, pageId: Int)
object serviceProtocol extends DefaultJsonProtocol with SprayJsonSupport {
    implicit val credsJson = jsonFormat1(createUserRequest.apply)
    implicit val credsJson2 = jsonFormat1(getProfileResponse.apply)
    implicit val credsJson3 = jsonFormat1(friendListResponse.apply)
    implicit val credsJson4 = jsonFormat2(updateProfileRequest.apply)
    implicit val credsJson5 = jsonFormat2(addFriendRequest.apply)
    implicit val credsJson6 = jsonFormat2(createPostRequest.apply)
    implicit val credsJson7 = jsonFormat2(updatePostRequest.apply)
    implicit val credsJson8 = jsonFormat1(deletePostRequest.apply)
    implicit val credsJson9 = jsonFormat1(getPostResponse.apply)
    implicit val credsJson10 = jsonFormat1(getPageResponse.apply)
    implicit val credsJson12 = jsonFormat1(createPageRequest.apply)
    implicit val credsJson13 = jsonFormat2(createPagePostRequest.apply)
    implicit val credsJson14 = jsonFormat1(followerListResponse.apply)
    implicit val credsJson15 = jsonFormat2(addPageFollowerRequest.apply)
}

class Worker(usrName: String) extends Actor{
	//import context._

	var userId: Int = 0
	var pageId: Int = 0
	var userName:String = usrName
	var friends =  Map[Int,Int]()
	var pages =  Map[Int,Int]()
	var masterRef: ActorRef = null
	var postCount: Int = 15
	var pagePostCount: Int = 25
	var postFrequency: Int = 500

	def receive = {
	  case Initiate => {
	    masterRef = sender
	    import serviceProtocol._
	    implicit val timeout = Timeout(5000)
	    
      val pipeline: HttpRequest => Future[String] = (sendReceive ~> unmarshal[String])
      val response: Future[String] = pipeline(Post("http://localhost:8000/createUser", createUserRequest(userName)))
      response.onComplete { 
        case Success(ret) => {
          updateUserId(ret, self)
          Thread.sleep(2000)
          self ! StartBrowsing
        }
        case Failure(ex) => {
          //return " "
        }
      }
	  }
	  case UpdateProfile(detailName: String, detailValue: String) => {
	    import serviceProtocol._
	    implicit val timeout = Timeout(5000)
	    
      val pipeline: HttpRequest => Future[String] = (sendReceive ~> unmarshal[String])
      val response: Future[String] = pipeline(Post("http://localhost:8000/" + userId + "/updateProfile", updateProfileRequest(detailName, detailValue)))
      response.onComplete { 
        case Success(ret) => {
          //println("returned value" + ret)
        }
        case Failure(ex) => {
          //return " "
        }
      }
	  }
	  case GetProfile() => {
	    import serviceProtocol._
	    implicit val timeout = Timeout(5000)
	    
      val pipeline: HttpRequest => Future[getProfileResponse] = (sendReceive ~> unmarshal[getProfileResponse])
      val response: Future[getProfileResponse] = pipeline(Get("http://localhost:8000/" + userId + "/getProfile"))
      response.onComplete { 
        case Success(ret) => {
         // println("returned value" + ret)
        }
        case Failure(ex) => {
          //return " "
        }
      }
	  }
	  case AddFriend(frndId: Int) => {
	    import serviceProtocol._
	    implicit val timeout = Timeout(5000)
	    
      val pipeline: HttpRequest => Future[String] = (sendReceive ~> unmarshal[String])
      val response: Future[String] = pipeline(Post("http://localhost:8000/" + userId + "/addFriend", addFriendRequest(userId, frndId)))
      response.onComplete { 
        case Success(ret) => {
          friends += (frndId -> 1)
          //println("returned value" + ret)
        }
        case Failure(ex) => {
          //return " "
        }
      }
	  }
	  case GetFriendList() => {
	    import serviceProtocol._
	    implicit val timeout = Timeout(5000)
	    
      val pipeline: HttpRequest => Future[friendListResponse] = (sendReceive ~> unmarshal[friendListResponse])
      val response: Future[friendListResponse] = pipeline(Get("http://localhost:8000/" + userId + "/getFriendList"))
      response.onComplete { 
        case Success(ret) => {
          //println("returned value" + ret)
        }
        case Failure(ex) => {
          //return " "
        }
      }
	  }
	  case CreatePost(message: String) => {
	    import serviceProtocol._
	    implicit val timeout = Timeout(5000)
	    
      val pipeline: HttpRequest => Future[String] = (sendReceive ~> unmarshal[String])
      val response: Future[String] = pipeline(Post("http://localhost:8000/" + userId + "/createPost", createPostRequest(userId, message)))
      response.onComplete { 
        case Success(ret) => {
          //println("returned value" + ret)
        }
        case Failure(ex) => {
          //return " "
        }
      }
	  }
	  case GetPosts() => {
	    import serviceProtocol._
	    implicit val timeout = Timeout(5000)
	    
      val pipeline: HttpRequest => Future[getPostResponse] = (sendReceive ~> unmarshal[getPostResponse])
      val response: Future[getPostResponse] = pipeline(Get("http://localhost:8000/" + userId + "/getPosts"))
      response.onComplete { 
        case Success(ret) => {
          //println("returned value" + ret)
        }
        case Failure(ex) => {
          //return " "
        }
      }
	  }
	  case UpdatePost(postId: Int, message: String) => {
	    import serviceProtocol._
	    implicit val timeout = Timeout(5000)
	    
      val pipeline: HttpRequest => Future[String] = (sendReceive ~> unmarshal[String])
      val response: Future[String] = pipeline(Post("http://localhost:8000/" + userId + "/updatePost", updatePostRequest(postId, message)))
      response.onComplete { 
        case Success(ret) => {
          //println("returned value" + ret)
        }
        case Failure(ex) => {
          //return " "
        }
      }
	  }
	  case DeletePost(postId: Int) => {
	    import serviceProtocol._
	    implicit val timeout = Timeout(5000)
	    
      val pipeline: HttpRequest => Future[String] = (sendReceive ~> unmarshal[String])
      val response: Future[String] = pipeline(Post("http://localhost:8000/" + userId + "/deletePost", deletePostRequest(postId)))
      response.onComplete { 
        case Success(ret) => {
          //println("returned value" + ret)
        }
        case Failure(ex) => {
          //return " "
        }
      }
	  }
	  case GetPostCount() => {
	    import serviceProtocol._
	    implicit val timeout = Timeout(5000)
	    
      val pipeline: HttpRequest => Future[String] = (sendReceive ~> unmarshal[String])
      val response: Future[String] = pipeline(Get("http://localhost:8000/" + userId + "/getPostCount"))
      response.onComplete { 
        case Success(ret) => {
          //println("returned value" + ret)
        }
        case Failure(ex) => {
          //return " "
        }
      }
	  }
	  case GetPage() => {
	    import serviceProtocol._
	    implicit val timeout = Timeout(5000)
	    
      val pipeline: HttpRequest => Future[getPageResponse] = (sendReceive ~> unmarshal[getPageResponse])
      val response: Future[getPageResponse] = pipeline(Get("http://localhost:8000/" + userId + "/getPostCount"))
      response.onComplete { 
        case Success(ret) => {
          //println("returned value" + ret)
       }
        case Failure(ex) => {
          //return " "
        }
      }
	  }
	  case CreatePage(pageName: String) => {
	    import serviceProtocol._
	    implicit val timeout = Timeout(5000)
	    
      val pipeline: HttpRequest => Future[String] = (sendReceive ~> unmarshal[String])
      val response: Future[String] = pipeline(Post("http://localhost:8000/createPage", createPageRequest(pageName)))
      response.onComplete { 
        case Success(ret) => {
          updatePageId(ret, self)
          self ! StartPagePosting
        }
        case Failure(ex) => {
          //return " "
        }
      }
	  }
	  case CreatePagePost(pageId: Int, message: String) => {
	    import serviceProtocol._
	    implicit val timeout = Timeout(5000)
	    
      val pipeline: HttpRequest => Future[String] = (sendReceive ~> unmarshal[String])
      val response: Future[String] = pipeline(Post("http://localhost:8000/" + pageId + "/createPagePost", createPagePostRequest(pageId, message)))
      response.onComplete { 
        case Success(ret) => {
          //println("returned value" + ret)
        }
        case Failure(ex) => {
          //return " "
        }
      }
	  }
	  case AddPageFollower(userId: Int, pageId: Int) => {
	    import serviceProtocol._
	    implicit val timeout = Timeout(5000)
	    
      val pipeline: HttpRequest => Future[String] = (sendReceive ~> unmarshal[String])
      val response: Future[String] = pipeline(Post("http://localhost:8000/" + pageId + "/addPageFollowerPost", addFriendRequest(userId, pageId)))
      response.onComplete { 
        case Success(ret) => {
          pages += (pageId -> 1)
          //println("returned value" + ret)
        }
        case Failure(ex) => {
          //return " "
        }
      }
	  }
	  case GetPageFollowerList(pageId: Int) => {
  	  import serviceProtocol._
	    implicit val timeout = Timeout(5000)
	    	    
      val pipeline: HttpRequest => Future[followerListResponse] = (sendReceive ~> unmarshal[followerListResponse])
      val response: Future[followerListResponse] = pipeline(Get("http://localhost:8000/" + pageId + "/getFollowerList"))
      response.onComplete { 
        case Success(ret) => {
        //  println("returned value" + ret)
        }
        case Failure(ex) => {
        //  return " "
        }
      }
	  }
	  case StartBrowsing => {
	    self ! CreateProfile
	  }  
	  case CreateProfile => { 
	    // Assigning Age
	    var rand = Random.nextInt(22)
	    var age = 18 + rand
	    var old: Int = 18
	    Global.userFriendCount += (userId -> 1 )
	    for(i <- Global.ages){
	      if(old < age && age < i){
	        var frndCount = Global.ageGroupCount(old)/2 + Random.nextInt(Global.ageGroupCount(old)/2)
	        Global.userFriendCount += (userId -> (frndCount + 1) )
	      }
	      old = i
	    }
	    
	    self ! UpdateProfile("Age", age.toString)
	    
	    // Assigning Gender
	    var rand2 = Random.nextInt(100)
	    if(Global.gender("M") > rand2){
	      self ! UpdateProfile("Gender", "M")
	    }else{
	      self ! UpdateProfile("Gender", "F")
	    }
	    
	    //Assigning email Id & Mobile Number
	    var rand3 = "%05d".format(Random.nextInt(10000)) + "%05d".format(Random.nextInt(10000))
	    self ! UpdateProfile("MobileNumber", rand3)
	    self ! UpdateProfile("Email", userName+"@facebook.com")
	    Thread.sleep(2000)
	    self ! CreateFriendList
	  }
	  case CreateFriendList => {
	    while(Global.userFriendCount(userId) > 0){
	      var rand = (Random.nextInt(Global.userCount) * 10) + 1	      
	      if(Global.userFriendCount.contains(rand) && Global.userFriendCount(rand) > 0 ){
	        if(rand != userId || !friends.contains(rand) ) self ! AddFriend(rand)
	        Global.userFriendCount += (rand -> (Global.userFriendCount(rand) - 1))
	        Global.userFriendCount += (userId -> (Global.userFriendCount(userId) - 1))
	      }
	    }
	    self ! StartPosting
	  }
	  case StartPosting => {
	    if (postCount > 1){
	      self ! CreatePost(System.currentTimeMillis.toString)
	      context.system.scheduler.scheduleOnce(postFrequency milliseconds,self, StartPosting)
	      postCount = postCount - 1
	    }else{
	      self ! FollowPages
	    }
	  }
	  case FollowPages => {
	    var pageFollowLimit = 15
	    while(pageFollowLimit > 0){
	      var rand = (Random.nextInt(Global2.pageCount) * 10) + 9	      
	      if(Global2.pagedata.contains(rand) && (rand != pageId || !pages.contains(rand)) ){
	        self ! AddPageFollower(userId, rand)
	        pageFollowLimit = pageFollowLimit - 1
	      }
	    }
	  }
	  case StartPagePosting => {
	    if (pagePostCount > 1 && pageId != 0){
	      self ! CreatePagePost(pageId, "Page - " + System.currentTimeMillis.toString)
	      context.system.scheduler.scheduleOnce(postFrequency milliseconds,self, StartPagePosting)
	      pagePostCount = pagePostCount - 1
	    }    
	  }
	}
	def updateUserId(uId: String, uActor: ActorRef){
	  userId = uId.toInt
	  Global.userdata += (userId -> uActor)
	  Global.userCount = Global.userCount + 1
	}
	
	def updatePageId(pId: String, pActor: ActorRef){
	  pageId = pId.toInt
	  Global2.pagedata += (pageId -> pActor)
	  Global2.pageCount = Global2.pageCount + 1
	}
}

object Global {
  var userCount = 0
  var userdata = scala.collection.mutable.Map[Int,ActorRef]()
  var userFriendCount = Map[Int,Int]()
	var frndstats = Map(18 -> 1050, 24 -> 900, 30 -> 700, 40 -> 550)
	var gender = Map("M" -> 45, "F" -> 55)
	var ageGroup = Map(18 -> 35, 24 -> 30, 30 -> 20, 40 -> 15)
	var frndCount = Map[Int,Int]()
	var genderCount = Map[String,Int]()
	var ageGroupCount = Map[Int,Int]()
	var ages = List(18,24,30,40)
	
	def Initialize(n: Int){
    userCount = n
	  for(age <- ages){
	    frndCount += (age -> ((frndstats(age)*n / 100) + 1).toInt)
	    ageGroupCount += (age -> ((ageGroup(age)*n /100) + 1).toInt)
	  }
	  genderCount += ("M" -> gender("M")*n /100 )
	  genderCount += ("F" -> gender("F")*n /100 ) 
	}
	
}
object Global2 {
  var pageCount = 0	
  var pagedata = scala.collection.mutable.Map[Int,ActorRef]()
}