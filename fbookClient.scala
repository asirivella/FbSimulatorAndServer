
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
import java.util.Base64
import java.security.KeyPairGenerator
import java.security._
import javax.crypto._
import java.security.spec.X509EncodedKeySpec
import java.security._
import javax.crypto.spec.SecretKeySpec
import javax.crypto.{Cipher, KeyGenerator, SecretKey}

sealed trait serviceMessage
case class Test() extends serviceMessage
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
case class UpdatePageProfile(detailName: String, detailValue: String) extends serviceMessage
case class GetProfile() extends serviceMessage
case class AddFriend(frndId: Int) extends serviceMessage
case class GetFriendListAndPost(task: String) extends serviceMessage
case class ShareMeYourPost(frndId: Int) extends serviceMessage
case class SharePost(id: Int, ref: ActorRef, key: PublicKey) extends serviceMessage
case class CreatePost(id: Int, key: String, message: String) extends serviceMessage
case class RecievePost(postId: Int, key: String, message: String) extends serviceMessage
case class GetPosts() extends serviceMessage
case class AuthenticateUser() extends serviceMessage
case class SendAuthKey(key: String) extends serviceMessage
case class UpdatePost(postId: Int, message: String) extends serviceMessage
case class DeletePost(postId: Int) extends serviceMessage
case class GetPostCount() extends serviceMessage
case class GetPage() extends serviceMessage
case class GetPagePostCount()  extends serviceMessage
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
	
	//Initializing stats
	Global.Initialize(numUsers)

//  println("user to be spawned : "+ totalUsers)    
	for (i <- 0 until numUsers){ 
		var user = context.actorOf(Props(new Worker("Actors" + i)), name = "Actors" + i)
		AllActors += user    // Hash map with user Id and corresponding actor	
		user ! Initiate
	}
	
	for (i <- 0 until numPages){ 
		var page = context.actorOf(Props(new Worker("Pages" + i)), name = "Pages" + i)
		AllActors += page    // Hash map with page Id and corresponding actor
	  page ! CreatePage("Pages"+i)
	}
	
	def receive = {
	  case Start => {
	    println("Master started with users :" + totalUsers)
	    if(numPages > 0) println("Master started with Pages :" + numPages)
	  }
	}
}

case class testRequest(key: String, text: String)
case class createUserRequest(publicKey: String, userName: String)
case class getProfileResponse(profile: Map[String,String])
case class friendListResponse(friendList: Map[String,String])
case class updateProfileRequest(detailName: String, key:String, detailValue: String)
case class updatePageProfileRequest(detailName: String, key:String, detailValue: String)
case class addFriendRequest(userId:Int, friendId: Int)
case class createPostRequest(userId:Int, friendId:Int, key: String, message: String)
case class updatePostRequest(postId:Int, key: String, message: String)
case class deletePostRequest(postId:Int)
case class getPostResponse(posts: Map[String,Map[String, String]])
case class getPageResponse(page: Map[String,String])
case class createPageRequest(publicKey: String, key: String, pageName: String)
case class createPagePostRequest(pageId: Int, key: String, message: String)
case class followerListResponse(followerList: Map[String,String])
case class addPageFollowerRequest(userId: Int, pageId: Int)
case class sendAuthKeyRequest(key: String)
object serviceProtocol extends DefaultJsonProtocol with SprayJsonSupport {
    implicit val credsJson = jsonFormat2(createUserRequest.apply)
    implicit val credsJson2 = jsonFormat1(getProfileResponse.apply)
    implicit val credsJson3 = jsonFormat1(friendListResponse.apply)
    implicit val credsJson4 = jsonFormat3(updateProfileRequest.apply)
    implicit val credsJson5 = jsonFormat2(addFriendRequest.apply)
    implicit val credsJson6 = jsonFormat4(createPostRequest.apply)
    implicit val credsJson7 = jsonFormat3(updatePostRequest.apply)
    implicit val credsJson8 = jsonFormat1(deletePostRequest.apply)
    implicit val credsJson9 = jsonFormat1(getPostResponse.apply)
    implicit val credsJson10 = jsonFormat1(getPageResponse.apply)
    implicit val credsJson12 = jsonFormat3(createPageRequest.apply)
    implicit val credsJson13 = jsonFormat3(createPagePostRequest.apply)
    implicit val credsJson14 = jsonFormat1(followerListResponse.apply)
    implicit val credsJson15 = jsonFormat2(addPageFollowerRequest.apply)
    implicit val credsJson16 = jsonFormat3(updatePageProfileRequest.apply)
    implicit val credsJson17 = jsonFormat1(sendAuthKeyRequest.apply)
    implicit val credsJson20 = jsonFormat2(testRequest.apply)
}

class Worker(usrName: String) extends Actor{
	//import context._

	var userId: Int = 0
	var pageId: Int = 0
	var userName:String = usrName
	var friends =  Map[Int,Int]()
	var pages =  Map[Int,Int]()
	var masterRef: ActorRef = null
	var postCount: Int = 5 + Random.nextInt(30)
	var pagePostCount: Int = 5 + Random.nextInt(40)
	var postFrequency: Int = 50

	var keyGen = KeyPairGenerator.getInstance("RSA")
  var base64Encoder = Base64.getUrlEncoder
  var base64Decoder = Base64.getUrlDecoder
  var aesKeyGen : KeyGenerator = KeyGenerator.getInstance("AES")
  keyGen.initialize(1024, SecureRandom.getInstance("SHA1PRNG", "SUN"))
  aesKeyGen.init(128)
  
  var aesCipher = Cipher.getInstance("AES")
  var rsaCipher = Cipher.getInstance("RSA")
  
  var keyPair = keyGen.generateKeyPair()
  var publicKey = keyPair.getPublic()
  var privateKey = keyPair.getPrivate()
  var secretKey = aesKeyGen.generateKey()
  var publicKeyString:String = base64Encoder.encodeToString(publicKey.getEncoded)
  val cipher : Cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING")
	val cipher_rsa : Cipher = Cipher.getInstance("RSA/ECB/PKCS1PADDING")
	
  //println(userId + ": Public Key : " + publicKey.toString)
  //println(userId + ": Private Key : " + privateKey.toString)

/*
	Test()
	
	def Test() {
	    import serviceProtocol._
	    implicit val timeout = Timeout(5000)
	    
	    println("test called")
	    var text = "No text can be seen here"
      var enc = encryptandgenerateKey(text)
      println(userId + ": encrypt text : " + enc("text"))
  
      println("secretKey : " + secretKey.toString)
      //var enc_test = encryptwithpublic(publicKey, secretKey)
	    
      val pipeline: HttpRequest => Future[testRequest] = (sendReceive ~> unmarshal[testRequest])
      val response: Future[testRequest] = pipeline(Post("http://localhost:8000/test", testRequest(enc("key"), enc("text"))))
                                        
      response.onComplete { 
        case Success(ret) => {
          
          //var drc_test: SecretKey = decryptwithprivate(ret.key);
          //var dec_text: String = decrypt(ret.text, drc_test)
          var dec_text: String = decryptwithKey(ret.key, ret.text)
          println(userId + ": decrypt text : " + dec_text.toString)

  println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$")
  println("secretKey : " + secretKey.toString)
  var enc_test2 = encryptwithpublic(ret.key, drc_test)
  println("encrypt with public key : " + enc_test2)
  var drc_test2 = decryptwithprivate(enc_test);
  println("decrypt with private key: " + drc_test2)
  var enc_text2 = encrypt(text, drc_test2)
  println(userId + ": encrypt text : " + enc_text2)
  var dec_text3 = decrypt(enc_text, drc_test)
  println(userId + ": decrypt text : " + dec_text3.toString)

          //println("returned value" + ret)
        }
        case Failure(ex) => {
          //return " "
        }
      }
	}
  var publicEnKey:String= base64Encoder.encodeToString(publicKey.getEncoded)
	var pubKeyInBytes: Array[Byte] = base64Decoder.decode(publicEnKey)
			var spec: X509EncodedKeySpec = new X509EncodedKeySpec(pubKeyInBytes)
			val keyfactory: KeyFactory = KeyFactory.getInstance("RSA")
			val pubkey: PublicKey = keyfactory.generatePublic(spec)
	println(" publick key : " + publicKey)
	println(" pub key decrypted : " + pubkey)

 */

	def receive = {
	  case Initiate => {
	    masterRef = sender
	    import serviceProtocol._
	    implicit val timeout = Timeout(5000)
	    
      val pipeline: HttpRequest => Future[String] = (sendReceive ~> unmarshal[String])
      val response: Future[String] = pipeline(Post("http://localhost:8000/createUser", createUserRequest(publicKeyString, userName)))
      response.onComplete { 
        case Success(ret) => {
          updateUserId(ret, self)
          println("user id : " + ret + "; user Name: " + userName + "; has joined")
          self ! AuthenticateUser()
        }
        case Failure(ex) => {
          //return " "
        }
      }
	  }
	  case AuthenticateUser() => {
	    import serviceProtocol._
	    implicit val timeout = Timeout(5000)
	    
      val pipeline: HttpRequest => Future[String] = (sendReceive ~> unmarshal[String])
      val response: Future[String] = pipeline(Get("http://localhost:8000/" + userId + "/getAuthKey"))
      response.onComplete { 
        case Success(ret) => {
          var decKey = decryptwithprivateKey(ret)
          self ! SendAuthKey(decKey.toString)
          //self ! SendAuthKey(ret)
          //println("returned value" + ret)
        }
        case Failure(ex) => {
          //return " "
        }
      }
	  }
	  
	  case SendAuthKey(key: String) => {
	    import serviceProtocol._
	    implicit val timeout = Timeout(5000)
	    
      val pipeline: HttpRequest => Future[String] = (sendReceive ~> unmarshal[String])
      val response: Future[String] = pipeline(Post("http://localhost:8000/" + userId + "/authenticateKey", sendAuthKeyRequest(encryptwithprivateKey(key))))
      response.onComplete { 
        case Success(ret) => {
          //println("userId : " + userId + "; Authorization status : " + ret)
          
          if(ret == "true"){
            self ! UpdateProfile("UserName", userName)
            Thread.sleep(1000)
            self ! StartBrowsing
          }else{
            println("Authentication failed for userId : " + userId + "; Authorization status : " + ret)
          }

          //println("returned value" + ret)
        }
        case Failure(ex) => {
          //return " "
        }
      }
	  }

	  case UpdateProfile(detailName: String, detailValue: String) => {
	    import serviceProtocol._
	    implicit val timeout = Timeout(5000)
	    
	    var encDetailValue = encryptandgenerateKey(detailValue) 
      val pipeline: HttpRequest => Future[String] = (sendReceive ~> unmarshal[String])
      val response: Future[String] = pipeline(Post("http://localhost:8000/" + userId + "/updateProfile", updateProfileRequest(detailName, encDetailValue("key"), encDetailValue("text"))))
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
         //CHANGES_NEEDED
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
	  case GetFriendListAndPost(task: String) => {
	    import serviceProtocol._
	    implicit val timeout = Timeout(5000)
	    
      val pipeline: HttpRequest => Future[friendListResponse] = (sendReceive ~> unmarshal[friendListResponse])
      val response: Future[friendListResponse] = pipeline(Get("http://localhost:8000/" + userId + "/getFriendList"))
      response.onComplete { 
        case Success(ret) => {
	        var frndIdSet = ret.friendList.keySet.toArray
	        var rand = Random.nextInt(frndIdSet.size)
	        var frndId = frndIdSet(rand)
	        if(task == "share") {
            self ! CreatePost(frndId.toInt, ret.friendList(frndId), System.currentTimeMillis.toString)
	        }else{
	          //println("SharePost called : at userId : " + userId + "; frndId : " + frndId) 
	          Global.userdata(frndId.toInt) ! SharePost(userId, self, publicKey)
	        }
          //println("returned value" + ret)
        }
        case Failure(ex) => {
          //return " "
        }
      }
	  }
	  case SharePost(frndId: Int, frndRef: ActorRef, pubKey: PublicKey) => {
	    import serviceProtocol._
	    implicit val timeout = Timeout(5000)
	    
      val pipeline: HttpRequest => Future[getPostResponse] = (sendReceive ~> unmarshal[getPostResponse])
      val response: Future[getPostResponse] = pipeline(Get("http://localhost:8000/" + userId + "/getPosts"))
      response.onComplete { 
        case Success(ret) => {
          var postIdSet = ret.posts.keySet.toArray
	        var rand = Random.nextInt(postIdSet.size)
	        var postId = postIdSet(rand)
	        var post = ret.posts(postId)
	        
	        var postMessage = decryptwithKey(post("key"), post("message"))
	        println("postID : " + postId + "; at user ID : " + userId + "; sending to : " + frndId + ";  message : " + post("message"))
	        println("postID : " + postId + "; at user ID : " + userId + "; sending to : " + frndId + "; decrypted message : " + postMessage)
	        
	        var secKey = aesKeyGen.generateKey() 
	        var encText = encrypt(postMessage, secKey)
	        var encKey = encryptwithpublic(pubKey, secKey)	
			    println("postID : " + postId + "; at user ID : " + userId + "; sending to : " + frndId + "; encrypted message : " + encText)

			    frndRef ! RecievePost(postId.toInt, encKey, encText)
			    
        }
        case Failure(ex) => {
          //return " "
        }
      }
	  }
	  case RecievePost(postId: Int, key: String, message: String) => {
	    var post = decryptwithKey(key, message)
	    println("Recieved: postID : " + postId + "; at user ID : " + userId + "; decrypted message : " + post)
	    
	  }
	  case CreatePost(frndId:Int, pubKey: String, message: String) => {
	    import serviceProtocol._
	    implicit val timeout = Timeout(5000)
	    
	    var secKey = aesKeyGen.generateKey() 
	    var pubKeyInBytes: Array[Byte] = base64Decoder.decode(pubKey)
			var pubKeyspec: X509EncodedKeySpec = new X509EncodedKeySpec(pubKeyInBytes)
			val keyfactory: KeyFactory = KeyFactory.getInstance("RSA")
			val frndPublicKey: PublicKey = keyfactory.generatePublic(pubKeyspec)

			cipher_rsa.init(Cipher.ENCRYPT_MODE,frndPublicKey)
			val encyptedRSAPublic: Array[Byte] = cipher_rsa.doFinal(secKey.getEncoded)
			val encSecKey: String = base64Encoder.encodeToString(encyptedRSAPublic)
			val encMessage: String = encrypt(message, secKey)
			
	    
      val pipeline: HttpRequest => Future[String] = (sendReceive ~> unmarshal[String])
      val response: Future[String] = pipeline(Post("http://localhost:8000/" + userId + "/createPost", createPostRequest(userId, frndId, encSecKey, encMessage)))
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
          //CHANGES_NEEDED
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
	    
	    var encMessage = encryptandgenerateKey(message)
      val pipeline: HttpRequest => Future[String] = (sendReceive ~> unmarshal[String])
      val response: Future[String] = pipeline(Post("http://localhost:8000/" + userId + "/updatePost", updatePostRequest(postId, encMessage("key"), encMessage("text"))))
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
          println("userId : " + userId + "; user post count : " + ret)
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
      val response: Future[getPageResponse] = pipeline(Get("http://localhost:8000/" + pageId + "/getPage"))
      response.onComplete { 
        case Success(ret) => {
          //CHANGES_NEEDED
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

	    
	    var encPageName = encryptandgenerateKey(pageName)
      val pipeline: HttpRequest => Future[String] = (sendReceive ~> unmarshal[String])      
      val response: Future[String] = pipeline(Post("http://localhost:8000/createPage", createPageRequest(publicKey.toString, encPageName("key"), encPageName("text"))))
      response.onComplete { 
        case Success(ret) => {
          updatePageId(ret, self)
          println("page Id : " + ret + "; Page Name: " + pageName + " has joined")
          Thread.sleep(1000)
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
	    
	    var encMessage = encryptandgenerateKey(message)
      val pipeline: HttpRequest => Future[String] = (sendReceive ~> unmarshal[String])
      val response: Future[String] = pipeline(Post("http://localhost:8000/" + pageId + "/createPagePost", createPagePostRequest(pageId, encMessage("key"), encMessage("text"))))
      response.onComplete { 
        case Success(ret) => {
          //println("returned value" + ret)
        }
        case Failure(ex) => {
          //return " "
        }
      }
	  }
	  case GetPagePostCount() => {
	    import serviceProtocol._
	    implicit val timeout = Timeout(5000)
	    
      val pipeline: HttpRequest => Future[String] = (sendReceive ~> unmarshal[String])
      val response: Future[String] = pipeline(Get("http://localhost:8000/" + pageId + "/getPagePostCount"))
      response.onComplete { 
        case Success(ret) => {
          println("pageId : " + pageId + "; page post count : " + ret)
          //println("returned value" + ret)
        }
        case Failure(ex) => {
         println("test - 4")   
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
          println("page Id : " + pageId + "; number of followers : " + ret.followerList.size)
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
	    if (postCount > 1 && userId > 0){
	      self ! GetFriendListAndPost("share")
	      postCount = postCount - 1
	      context.system.scheduler.scheduleOnce(postFrequency milliseconds,self, StartPosting)
	    }else{
	      //self ! GetPostCount()
	      self ! FollowPages()
	      self ! GetFriendListAndPost("dummy")
	    }
	  }
	  case FollowPages => {
	    var pageFollowLimit = Random.nextInt(Global2.pageCount)
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
	    }else{
	      self ! GetPagePostCount()
	      //self ! GetPageFollowerList(pageId)
	    }
	  }
	}
	def encryptandgenerateKey(text: String): Map[String, String] = {
	  var secKey = aesKeyGen.generateKey() 
	  var encText = encrypt(text, secKey)
	  var encKey = encryptwithpublic(publicKey, secKey)	  
	  var encHash = Map("key" -> encKey, "text" -> encText) 
	  return encHash
	}
	
	def decryptwithKey(key: String, enc_text: String): String = {
	  var decKey: SecretKey = decryptwithprivate(key)
    var decText: String = decrypt(enc_text, decKey)
    return decText
	}
	
  def encrypt(plainText:String, secKey: SecretKey): String = {
    cipher.init(Cipher.ENCRYPT_MODE,secKey)
		var textArr : Array[Byte] = plainText.getBytes("utf-8")
		var encryptedByte : Array[Byte] = cipher.doFinal(textArr)
		var encryptedText : String = base64Encoder.encodeToString(encryptedByte)			
		return encryptedText
	}

	def decrypt(encryptedText:String, secKey: SecretKey): String = {
	  cipher.init(Cipher.DECRYPT_MODE,secKey)
		var encryptedTextByte : Array[Byte] = base64Decoder.decode(encryptedText.toString())
		var decryptedByte : Array[Byte] = cipher.doFinal(encryptedTextByte)
		var decrypted_text : String = new String(decryptedByte)
		return decrypted_text
	}

	def encryptwithpublic(pubKey: PublicKey,secKey :SecretKey): String = {
		cipher_rsa.init(Cipher.ENCRYPT_MODE,pubKey)
		var encryptedByteKey : Array[Byte] = cipher_rsa.doFinal(secKey.getEncoded)
		var encryptedByteKeyString : String = base64Encoder.encodeToString(encryptedByteKey)
		return encryptedByteKeyString
	}

	def decryptwithprivate(seckey: String): SecretKey = {
		cipher_rsa.init(Cipher.DECRYPT_MODE, privateKey)
		var secretKeyArr : Array[Byte] = base64Decoder.decode(seckey)
		val decryptedByteKey :Array[Byte] = cipher_rsa.doFinal(secretKeyArr)
		val secKey : SecretKeySpec = new SecretKeySpec(decryptedByteKey,"AES")
		return secKey
	}
	
	def encryptwithprivateKey(text: String): String = {
		cipher_rsa.init(Cipher.ENCRYPT_MODE,privateKey)
		var textArr : Array[Byte] = text.getBytes("utf-8")
		var encryptedByte : Array[Byte] = cipher_rsa.doFinal(textArr)
		var encryptedByteString : String = base64Encoder.encodeToString(encryptedByte)
		return encryptedByteString
	}
	
	def decryptwithprivateKey(text: String): String = {
	  cipher_rsa.init(Cipher.DECRYPT_MODE, privateKey)
		var decryptedTextByte : Array[Byte] = base64Decoder.decode(text)
	  var decryptedByte : Array[Byte] = cipher_rsa.doFinal(decryptedTextByte)
		var decrypted_text : String = new String(decryptedByte)
		return decrypted_text
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