import spray.routing._

import akka.io._
import spray.can.Http
import akka.actor._
import spray.http._
import spray.json._
import MediaTypes._
import akka.pattern.ask
import akka.util.Timeout
import spray.httpx.SprayJsonSupport
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.util.{Success,Failure}
import DefaultJsonProtocol._
import spray.routing.RejectionHandler.Default
import spray.routing.{RoutingSettings, RejectionHandler, ExceptionHandler, HttpService}

import org.json4s._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write,writePretty}
import java.util.Base64
import java.security.KeyPairGenerator
import java.security._
import javax.crypto._
import java.security.spec.X509EncodedKeySpec
import java.security._
import javax.crypto.spec.SecretKeySpec
import javax.crypto.{Cipher, KeyGenerator, SecretKey}

sealed trait serviceMessage

object fbServer extends App with SimpleRoutingApp{
  implicit val system = ActorSystem("fbServer")
  val service = system.actorOf(Props[SprayActor], "SprayActor")
  IO(Http) ! Http.Bind(service, interface = "localhost", port = 8000)
  
  println("Server Hosted")
}

case class testRecieve(key: String, text: String)
case class createUserReceive(userName: String, publicKey: String)
case class profileResponse(profile: Map[String,Map[String,String]])
case class friendListResponse(friendList: Map[String,String])
case class updateProfileReceive(detailName: String, key: String, detailValue: String)
case class addFriendReceive(userId:Int, friendId: Int)
case class createPostReceive(userId:Int, friendId: Int, key: String, message: String)
case class updatePostReceive(postId:Int, key: String, message: String)
case class deletePostReceive(postId:Int)
case class authenticateKeyReceive(key:String)
case class getPostResponse(posts: Map[String,Map[String, String]])
case class getPageResponse(page: Map[String,Map[String, String]])
case class createPageReceive(publicKey: String, key: String, pageName: String)
case class createPagePostReceive(pageId: Int, key: String, message: String)
case class followerListResponse(followerList: Map[String,String])
case class addPageFollowerReceive(userId: Int, pageId: Int)
object serviceProtocol extends DefaultJsonProtocol with SprayJsonSupport {
    implicit val credsJson = jsonFormat2(createUserReceive.apply)
    implicit val credsJson3 = jsonFormat1(profileResponse.apply)
    implicit val credsJson4 = jsonFormat1(friendListResponse.apply)
    implicit val credsJson5 = jsonFormat3(updateProfileReceive.apply)
    implicit val credsJson6 = jsonFormat2(addFriendReceive.apply)
    implicit val credsJson7 = jsonFormat4(createPostReceive.apply)
    implicit val credsJson8 = jsonFormat3(updatePostReceive.apply)
    implicit val credsJson9 = jsonFormat1(deletePostReceive.apply)
    implicit val credsJson10 = jsonFormat1(getPostResponse.apply)
    implicit val credsJson11 = jsonFormat1(getPageResponse.apply)
    implicit val credsJson12 = jsonFormat3(createPageReceive.apply)
    implicit val credsJson13 = jsonFormat3(createPagePostReceive.apply)
    implicit val credsJson14 = jsonFormat1(followerListResponse.apply)
    implicit val credsJson15 = jsonFormat2(addPageFollowerReceive.apply)
    implicit val credsJson16 = jsonFormat1(authenticateKeyReceive.apply)
    implicit val credsJson20 = jsonFormat2(testRecieve.apply)
}

class SprayActor extends Actor with SprayService{
  def actorRefFactory = context
  def receive = runRoute(
      //default ~ hello ~
                          SprayCreateUserRoute ~ TestRoute ~ SprayGetPublicKeyRoute ~
                          SprayUpdateProfileRoute ~ SprayGetProfileRoute ~ 
                          SprayAddFriendRoute ~ SprayGetFriendListRoute ~ SprayCreatePostRoute ~
                          SprayGetPostsRoute ~ SprayUpdatePostRoute ~ SprayDeletePostRoute ~
                          SprayGetPostCountRoute ~ SprayGetPageRoute ~ SprayCreatePageRoute ~ 
                          SprayCreatePagePostRoute ~ SprayAddPageFollowerRoute ~ SprayGetPageFollowerListRoute ~
                          SprayGetPagePostCountRoute ~ SprayGetAuthKeyRoute ~ SprayAuthenticateKeyRoute
                          )

}

trait SprayService extends HttpService {
  import serviceProtocol._
  import scala.concurrent.ExecutionContext.Implicits.global

  def default = {
      path("") {
      get {
        complete {
          "<h1>Server Is running</h1>"
        }
      }
    }
  }  
  def hello = {
      path("hello") {
      get {
        complete {
          "<h1>Say hello to spray</h1>"
        }
      }
    }
  }
    
  def TestRoute = {
      path("test") {
      post {
        entity(as[testRecieve]) { testDetails =>
          val result = testDetails
          println("result text : " + result.text)
          complete(testRecieve(result.key, result.text))
        }
      }
    }
  }

  def SprayCreateUserRoute = {
    path("createUser"){
      post {
        entity(as[createUserReceive]) { createDetails =>
          val result = CreateUser(createDetails.userName, createDetails.publicKey)
          complete(result.toString)
        }
      }
    }
  }

  def SprayGetPublicKeyRoute = {
    path(Segment / "publicKey"){ userId =>
      get {
        val result = GetPublicKey(userId.toInt)
        complete(result.toString)
      }
    }
  }
  
  def SprayUpdateProfileRoute = {
    path(Segment / "updateProfile"){ userId =>
      post {
        entity(as[updateProfileReceive]) { updateDetails =>
          var result = UpdateProfile(userId.toInt, updateDetails.detailName, updateDetails.key, updateDetails.detailValue)
          complete(result.toString)
        }
      }
    }
  }
  def SprayGetProfileRoute = {
    path(Segment / "getProfile"){ userId =>
      get { 
        var result = GetProfile(userId.toInt)
        complete(profileResponse(result))
      }
    }
  }
  def SprayAddFriendRoute = {
    path(Segment / "addFriend"){ userId =>
      post {
        entity(as[addFriendReceive]) { frndDetails =>
          var result = AddFriend(frndDetails.userId, frndDetails.friendId)
          complete(result.toString)
        }
      }
    }
  }
  def SprayGetFriendListRoute = {
    path(Segment / "getFriendList"){ userId =>
      get { 
        var result = GetFriendList(userId.toInt)
        complete(friendListResponse(result))
      }
    }
  }
  def SprayCreatePostRoute = {
    path(Segment / "createPost"){ userId =>
      post {
        entity(as[createPostReceive]) { createPostDetails =>
          var result = CreatePost(createPostDetails.userId, createPostDetails.friendId, createPostDetails.key, createPostDetails.message)
          complete(result.toString)
        }
      }
    }
  }
  def SprayGetPostsRoute = {
    path(Segment / "getPosts"){ userId =>
      get { 
        var result = GetPosts(userId.toInt)
        complete(getPostResponse(result))
      }
    }
  }
  def SprayUpdatePostRoute = {
    path(Segment / "updatePost"){ userId =>
      post {
        entity(as[updatePostReceive]) { updatePostDetails =>
          var result = UpdatePost(updatePostDetails.postId, updatePostDetails.key, updatePostDetails.message)
          complete(result.toString)
        }
      }
    }
  }
  def SprayDeletePostRoute = {
    path(Segment / "deletePost"){ userId =>
      post {
        entity(as[deletePostReceive]) { deletePostDetails =>
          var result = DeletePost(deletePostDetails.postId)
          complete(result.toString)
        }
      }
    }
  }
  def SprayGetPostCountRoute = {
    path(Segment / "getPostCount"){ userId =>
      get { 
        var result = GetPostCount(userId.toInt)
        complete(result.toString)
      }
    }
  }
  def SprayGetAuthKeyRoute = {
    path(Segment / "getAuthKey"){ userId =>
      get { 
        var result = GetAuthKey(userId.toInt)
        complete(result.toString)
      }
    }
  }
  def SprayAuthenticateKeyRoute = {
    path(Segment / "authenticateKey"){ userId =>
      post {
        entity(as[authenticateKeyReceive]) { authenticateDetails =>
          var result = AuthenticateKey(userId.toInt, authenticateDetails.key)
          complete(result.toString)
        }
      }
    }
  }
  def SprayGetPageRoute = {
    path(Segment / "getPage"){ pageId =>
      get { 
        var result = GetPage(pageId.toInt)
        complete(getPageResponse(result))
      }
    }
  }
  def SprayCreatePageRoute = {
    path("createPage"){
      post {
        entity(as[createPageReceive]) { createPageDetails =>
          val result = CreatePage(createPageDetails.publicKey, createPageDetails.key, createPageDetails.pageName)
          complete(result.toString)
        }
      }
    }
  }
  
  def SprayCreatePagePostRoute = {
    path(Segment / "createPagePost"){ pageId =>
      post {
        entity(as[createPagePostReceive]) { createPagePostDetails =>
          var result = CreatePagePost(createPagePostDetails.pageId, createPagePostDetails.key, createPagePostDetails.message)
          complete(result.toString)
        }
      }
    }
  }
  
  def SprayGetPagePostCountRoute = {
    path(Segment / "getPagePostCount"){ pageId =>
      get { 
        var result = GetPagePostCount(pageId.toInt)
        complete(result.toString)
      }
    }
  }
  
  def SprayAddPageFollowerRoute = {
    path(Segment / "addPageFollower"){ userId =>
      post {
        entity(as[addPageFollowerReceive]) { addPageFollowerDetails =>
          var result = AddPageFollower(addPageFollowerDetails.userId, addPageFollowerDetails.pageId)
          complete(result.toString)
        }
      }
    }
  }
  def SprayGetPageFollowerListRoute = {
    path(Segment / "getFollowerList"){ pageId =>
      get { 
        var result = GetPageFollowerList(pageId.toInt)
        complete(followerListResponse(result))
      }
    }
  }
  
  // Helper functions for retrieving User information
  def CreateUser(userName:String, pubKey: String): Int = {
    Global.userIdCounter+= 10
    var userId = Global.userIdCounter 
    var newUser = new UserInfo(userId, pubKey)
    Global.userdata += (newUser.userId -> newUser)
    //newUser.UpdateProfile("UserName", userName)
    return  newUser.userId
  }
  def GetPublicKey(id: Int): String = {
    return Global.userdata(id).publicKey
  }
  def UpdateProfile(id: Int, detailName: String, key: String, detailValue: String) = {
    Global.userdata(id).UpdateProfile(detailName, key, detailValue)
  }
  def GetProfile(id: Int): Map[String, Map[String, String]] = {
    return Global.userdata(id).GetProfile()
  }
  def GetAuthKey(id: Int): String = {
    return Global.userdata(id).GetAuthKey()
  }
  def AuthenticateKey(id: Int, key: String): String = {
    return Global.userdata(id).AuthenticateKey(key).toString
  }
  def AddFriend(id: Int, frndId: Int) = {
    if(Global.userdata(id).friendList.contains(frndId.toString) || id == frndId){
      // Do nothing
    }else{
      Global.userdata(id).AddFriend(frndId)
      Global.userdata(frndId).AddFriend(id)
      println(id + " is friend of " + frndId)
    }
  }
  def GetFriendList(id: Int): Map[String, String] = {
    return Global.userdata(id).GetFriendList()
  }
  /*
  def GetFriendListDetailed(id: Int): ArrayBuffer[UserInfo] = {
    var frndList = Global.userdata(id).GetFriendList()
    var frndDetail = Map[Int, String]()
    frndList.foreach(i => frndDetail += (Global.userdata(i).userId -> Global.userdata(i).profile("userName")))
  }
  */
  def CreatePost(id:Int, frndId: Int, key: String, msg: String) = {
      Global2.postIdCounter += 10
      var user = Global.userdata(id)
      var frndUser = Global.userdata(frndId)
      var post = new post(Global2.postIdCounter, id, frndId, key, msg)
      Global2.postdata += (post.postId -> post)
      frndUser.CreatePost(post.postId, post)
      user.SharePost(post)
  }
  def GetPosts(id:Int): Map[String, Map[String, String]] = {
    var user = Global.userdata(id)
    return user.GetPosts()
  }
  def UpdatePost(postId: Int, key: String, msg: String) = {
    Global2.postdata(postId).encKey = key
    Global2.postdata(postId).message = msg
  }
  def DeletePost(postId: Int) = {
    var post = Global2.postdata(postId)
    var userId = post.sharedToWallOf
    
    Global.userdata(userId).DeletePost(postId)
    //Global2.postdata -= (postId -> post)
  }
  def GetPostCount(id: Int): Int = {
    return Global.userdata(id).PostCount()
  }
  def CreatePage(publicKey: String, key: String, pageName:String): Int = {
    Global3.pageIdCounter += 10
    var pageId = Global3.pageIdCounter 
    var newPage = new PageInfo(pageId, publicKey, key, pageName)
    Global3.pagedata += (newPage.pageId -> newPage)
    return  newPage.pageId
  }
  def CreatePagePost(id:Int, key: String, msg: String) = {
      Global2.postIdCounter += 10
      var page = Global3.pagedata(id)
      var post = new post(Global2.postIdCounter, id, id, key, msg)
      Global2.postdata += (post.postId -> post)
      page.CreatePost(post.postId, post)
  }
  def GetPagePostCount(id: Int): Int = {
    var posts = GetPage(id)
    return posts.size
  }
  def GetPage(id: Int): Map[String,Map[String, String]] = {
    var page = Global3.pagedata(id)
    return page.GetPosts()
  }
  def AddPageFollower(id: Int, pId: Int) = {
    Global3.pagedata(pId).AddFollower(id)
  }
  def GetPageFollowerList(id: Int): Map[String, String] = {
    return Global3.pagedata(id).GetFollowerList()
  }
}

class UserInfo(id:Int, pubKey: String){													// class for users information
  import serviceProtocol._
  
	var userId : Int = id
	var posts = Map[String,post]()
	var profile = Map[String,Map[String,String]]()
  var friendList = Map[String,String]()
  var pages = Map[String,String]()
  var publicKey: String = pubKey
  
  var base64Encoder = Base64.getUrlEncoder
  var base64Decoder = Base64.getUrlDecoder
  
  var aesCipher = Cipher.getInstance("AES")
  var rsaCipher = Cipher.getInstance("RSA")
  val cipher : Cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING")
	val cipher_rsa : Cipher = Cipher.getInstance("RSA/ECB/PKCS1PADDING")
	
	var random = SecureRandom.getInstance("SHA1PRNG");
  var randomKey1 = new Array[Byte](32)
  var randomKey2 = new Array[Byte](32) 
    
  random.nextBytes(randomKey1)
  random.nextBytes(randomKey2)
  

  def AddFriend(frndId:Int) = {
    var frnd = Global.userdata(frndId)
    friendList += (frndId.toString -> frnd.publicKey)
    
    for ((postId, post) <- posts){
      post.UpdateShare(frndId)
    }
  }
  def GetFriendList():Map[String,String] = {
    return friendList
  }
  def GetAuthKey(): String = {
    randomKey2 = randomKey1
    random.nextBytes(randomKey1)
    var key = encryptwithpublic(randomKey2.toString)
    return key
  }
  def AuthenticateKey(key: String): Boolean = {
    var num = decryptwithpublic(key)
    if(randomKey2.toString == num.toString){
      randomKey2 = randomKey1
      random.nextBytes(randomKey1)
      println("userId : " + userId + "; Authorization status : " + true)
      return true
    }else{
      randomKey2 = randomKey1
      random.nextBytes(randomKey1)
      return false
    }
  }
  def UpdateProfile(detailName: String, key: String, detail: String) = {
    //UserName, Gender, Age, Email, MobileNumber
    /*
    profile += ("UserName" -> userName)
    profile += ("Gender" -> "Male")
    profile += ("Age" -> "24")
    profile += ("Email" -> (userName + "@facebook.com"))
    profile += ("MobileNumber" -> "99999")  
    */
//    if(detailName == "UserName" || detailName == "Gender" || 
//        detailName == "Age" || detailName == "Email" || detailName == "MobileNumber"){
    println("user : "+ userId + "; profile update : " + detailName + " : " + detail)
    var temp = Map("key" -> key, "value" -> detail)
      profile += (detailName -> temp)
  } 
  
  def GetProfile():Map[String, Map[String, String]] = {
    return profile
  }
  def CreatePost(id: Int, pst: post) = {
    posts += (id.toString -> pst)
    friendList foreach{
      case(i, frnd) => pst.UpdateShare(i.toInt) 
    }
    println("User ID : " + pst.sharedBy + "; posted on : " + userId + "; post Created : " + id + "; Message : " + pst.message)
  }
  def SharePost(pst: post) = {
    friendList foreach{
      case(i, frnd) => pst.UpdateShare(i.toInt) 
    }
  }
  def GetPosts(): Map[String, Map[String, String]] = {
    var postdata = Map[String, Map[String, String]]()
    posts foreach{
      case (postId, post) =>
        var temp = Map("key" -> post.encKey, "message" -> post.message)
        postdata += (postId.toString() -> temp)
    }
    return postdata
  }
  def UpdatePost(id: String, msg: String) = {
    posts(id).UpdatePost(msg)
  }
  def DeletePost(id: Int) = {
    //remove(posts(id))
    posts -= id.toString
  }
  def PostCount(): Int = {
    return posts.size
  }
  def GetAllPost(): Map[String, post] = {
    return posts
  }
  def FollowPage(pageId: Int) = {
    pages += (pageId.toString -> Global3.pagedata(pageId).pageName)
  }
  
  def GetPublicKey(): PublicKey = {
		var pubKeyInBytes: Array[Byte] = base64Decoder.decode(publicKey)
		var pubKeySpec: X509EncodedKeySpec = new X509EncodedKeySpec(pubKeyInBytes)
    val keyfactory: KeyFactory = KeyFactory.getInstance("RSA")
		val publickey: PublicKey = keyfactory.generatePublic(pubKeySpec)
		return publickey
  }
	def encryptwithpublic(text: String): String = {
		var encryptedByte: Array[Byte] = text.getBytes("utf-8")		
		cipher_rsa.init(Cipher.ENCRYPT_MODE, GetPublicKey())
		var encryptedByteKey: Array[Byte] = cipher_rsa.doFinal(encryptedByte)
		var encryptedByteKeyString: String = base64Encoder.encodeToString(encryptedByteKey)
		return encryptedByteKeyString
	}
	def decryptwithpublic(text:String): String = {
		var decBytes: Array[Byte] = base64Decoder.decode(text)
		var pubKey: PublicKey = GetPublicKey()
		cipher_rsa.init(Cipher.DECRYPT_MODE, pubKey)
		var decKey: Array[Byte] = cipher_rsa.doFinal(decBytes)
		var decKeyString: String = new String(decKey)
		return decKeyString
	}

}

class post(id: Int, by: Int, to: Int, key: String, msg: String){
  var message = msg
  var postId: Int = id
	var sharedBy: Int = by
	var encKey:String = key
	var sharedToWallOf = to
	var sharedTo = new ArrayBuffer[Int]
  
  def UpdateShare(userId: Int) = {
    sharedTo += userId
  }
  def UpdatePost(msg: String) = {
    message = msg
  }
}


class PageInfo(id:Int, pubKey: String, key: String, name: String){													// class for Pages information
  import serviceProtocol._
  
	var pageId : Int = id
	var pageName : String = name
	var pageNameKey: String = key
	var publicKey: String = pubKey
	var posts = Map[String,post]()
  var followerList = Map[String, String]() 

  def AddFollower(flwId:Int) = {
    var flw = Global.userdata(flwId)
    followerList += (flwId.toString -> flw.publicKey)
    println("User " + flw.userId + " is following page " + pageName )
    flw.FollowPage(pageId)
  }
  def CreatePost(id: Int, pst: post) = {
    posts += (id.toString -> pst)
    followerList foreach{
      case(i, frnd) => pst.UpdateShare(i.toInt) 
    }
    println("Page Id : " + pageId + "; Page Post Created : " + id + " - " + pst.message)
  }
  def GetFollowerList():Map[String, String] = {
    return followerList
  }
  def GetPosts(): Map[String, Map[String, String]] = {
    var postdata = Map[String,Map[String, String]]()
    posts foreach{
      case (postId, post) =>
          var temp = Map("key" -> post.encKey , "message" -> post.message)
          postdata += (postId.toString() -> temp)
    }
    return postdata
  }
  def UpdatePost(id: String, msg: String) = {
    posts(id).UpdatePost(msg)
  }
  def DeletePost(id: Int) = {
    //remove(posts(id))
    posts -= id.toString
  }
}


object Global {
	var userdata = scala.collection.mutable.Map[Int,UserInfo]()
	var userIdCounter: Int = 1
}

object Global2 {
  var postdata = scala.collection.mutable.Map[Int,post]()
	var postIdCounter: Int = 5
}

object Global3 {
	var pagedata = scala.collection.mutable.Map[Int,PageInfo]()
	var pageIdCounter: Int = 9
}