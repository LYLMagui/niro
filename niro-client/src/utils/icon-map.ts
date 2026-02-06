import type { Component } from "vue";
import {
  DashboardIcon,
  ServerIcon,
  UserCircleIcon,
  PoweroffIcon,
  HomeIcon,
  FileIcon,
  ChartIcon,
  NotificationIcon,
  CloudIcon,
  AppIcon,
  BookmarkIcon,
  BrowseIcon,
  CalendarIcon,
  CallIcon,
  CameraIcon,
  ClearIcon,
  BackupIcon,
  CodeIcon,
  ControlPlatformIcon,
  CreditcardIcon,
  DataIcon,
  DeleteIcon,
  DiscountIcon,
  DownloadIcon,
  Edit1Icon,
  MailIcon,
  EnterIcon,
  ExploreIcon,
  FileExcelIcon,
  FileImageIcon,
  FilePasteIcon,
  FilePowerpointIcon,
  FileSearchIcon,
  FileWordIcon,
  FilterIcon,
  FingerprintIcon,
  FolderIcon,
  FolderOpenIcon,
  TipsIcon,
  HelpIcon,
  GiftIcon,
  ImageIcon,
  InfoCircleIcon,
  InstallIcon,
  JumpIcon,
  KeyboardIcon,
  AnchorIcon,
  LayersIcon,
  LayoutIcon,
  LinkIcon,
  LoadingIcon,
  LocationIcon,
  LockOnIcon,
  LogoGithubIcon,
  MenuIcon,
  MinusIcon,
  MobileIcon,
  MoneyIcon,
  MoveIcon,
  MapIcon,
  PageFirstIcon,
  PageLastIcon,
  PauseIcon,
  PlayIcon,
  PlusIcon,
  PreviousIcon,
  PrintIcon,
  QrcodeIcon,
  RefreshIcon,
  ArrowDownIcon,
  ArrowLeftIcon,
  ArrowRightIcon,
  ArrowUpIcon,
  ScreenshotIcon,
  SearchIcon,
  SendIcon,
  ServiceIcon,
  SoundIcon,
  StarFilledIcon,
  StopIcon,
  SwapIcon,
  TaskIcon,
  TimeIcon,
  ToolsIcon,
  UploadIcon,
  UsbIcon,
  UserAddIcon,
  UserBlockedIcon,
  UserCheckedIcon,
  UsergroupIcon,
  UserSearchIcon,
  UserTalkIcon,
  VideoIcon,
  VideoCameraIcon,
  WalletIcon,
  FogNightIcon,
  WifiIcon,
  ZoomInIcon,
  ZoomOutIcon,
  SettingIcon,
  ShareIcon,
  BulletpointIcon,
  ViewListIcon,
  OrderAscendingIcon,
  HistoryIcon,
  ShopIcon,
  UserIcon,
} from "tdesign-icons-vue-next";

export type IconName =
  | "dashboard"
  | "system"
  | "user"
  | "login"
  | "logout"
  | "home"
  | "exception"
  | "chart"
  | "notification"
  | "cloud"
  | "app"
  | "bookmark"
  | "browse"
  | "calendar"
  | "call"
  | "camera"
  | "catalog"
  | "clear"
  | "cloud-backup"
  | "code"
  | "control"
  | "credit-card"
  | "customer-service"
  | "data"
  | "database"
  | "delete"
  | "discount"
  | "download"
  | "edit"
  | "email"
  | "enter"
  | "explore"
  | "file"
  | "file-excel"
  | "file-image"
  | "file-paste"
  | "file-powerpoint"
  | "file-protect"
  | "file-search"
  | "file-text"
  | "file-word"
  | "filter"
  | "find"
  | "fingerprint"
  | "flow"
  | "folder"
  | "folder-open"
  | "format-painter"
  | "friend"
  | "gif"
  | "goods"
  | "grid"
  | "help"
  | "history"
  | "hongbao"
  | "image"
  | "info-circle"
  | "info-fill"
  | "insert-row-top"
  | "installation"
  | "jump"
  | "keyboard"
  | "label"
  | "launch"
  | "layers"
  | "layout"
  | "link"
  | "list"
  | "loading"
  | "location"
  | "lock"
  | "logo-alipay"
  | "logo-android"
  | "logo-apple"
  | "logo-chrome"
  | "logo-codepen"
  | "logo-github"
  | "logo-ie"
  | "logo-instagram"
  | "logo-qq"
  | "logo-twitter"
  | "logo-wechat"
  | "logo-whatsapp"
  | "logo-youtube"
  | "magic"
  | "mail"
  | "menu"
  | "message"
  | "min"
  | "mobile"
  | "money"
  | "monitor"
  | "more"
  | "move"
  | "mp"
  | "music"
  | "navigate"
  | "news"
  | "night"
  | "null"
  | "offline"
  | "order"
  | "organization"
  | "overview"
  | "pad"
  | "page-first"
  | "page-last"
  | "palette"
  | "pause"
  | "pdf"
  | "pin"
  | "play-circle"
  | "play"
  | "plus"
  | "power"
  | "previous"
  | "print"
  | "prompt"
  | "qrcode"
  | "question-circle"
  | "redux"
  | "refresh"
  | "reload"
  | "ridicule"
  | "rollback"
  | "root-list"
  | "round-arrow-down"
  | "round-arrow-left"
  | "round-arrow-right"
  | "round-arrow-up"
  | "screenshot"
  | "search"
  | "secure"
  | "send"
  | "server"
  | "server-circle"
  | "service"
  | "setting"
  | "setup"
  | "share"
  | "shop"
  | "shopping-cart"
  | "slideshow"
  | "sound"
  | "star-fill"
  | "star"
  | "stop"
  | "swap"
  | "task"
  | "time"
  | "timer"
  | "tips"
  | "tools"
  | "trash-can"
  | "unbind"
  | "unlock"
  | "upload"
  | "usb"
  | "user-add"
  | "user-blocked"
  | "user-check"
  | "user-delete"
  | "user-group"
  | "user-search"
  | "user-talk"
  | "video"
  | "video-camera"
  | "view-list"
  | "voice"
  | "wallet"
  | "warning-circle"
  | "wifi"
  | "zoom-in"
  | "zoom-out";

const iconMap: Record<IconName, Component> = {
  dashboard: DashboardIcon,
  system: ServerIcon,
  user: UserIcon,
  login: UserCircleIcon,
  logout: PoweroffIcon,
  home: HomeIcon,
  exception: NotificationIcon,
  chart: ChartIcon,
  notification: NotificationIcon,
  cloud: CloudIcon,
  app: AppIcon,
  bookmark: BookmarkIcon,
  browse: BrowseIcon,
  calendar: CalendarIcon,
  call: CallIcon,
  camera: CameraIcon,
  clear: ClearIcon,
  "cloud-backup": BackupIcon,
  code: CodeIcon,
  control: ControlPlatformIcon,
  "credit-card": CreditcardIcon,
  "customer-service": ServiceIcon,
  data: DataIcon,
  database: FileIcon,
  delete: DeleteIcon,
  discount: DiscountIcon,
  download: DownloadIcon,
  edit: Edit1Icon,
  email: MailIcon,
  enter: EnterIcon,
  explore: ExploreIcon,
  file: FileIcon,
  "file-excel": FileExcelIcon,
  "file-image": FileImageIcon,
  "file-paste": FilePasteIcon,
  "file-powerpoint": FilePowerpointIcon,
  "file-protect": FileIcon,
  "file-search": FileSearchIcon,
  "file-text": FileIcon,
  "file-word": FileWordIcon,
  filter: FilterIcon,
  find: FileSearchIcon,
  fingerprint: FingerprintIcon,
  flow: FileIcon,
  folder: FolderIcon,
  "folder-open": FolderOpenIcon,
  "format-painter": TipsIcon,
  friend: GiftIcon,
  gif: GiftIcon,
  grid: FolderIcon,
  help: HelpIcon,
  history: HistoryIcon,
  hongbao: GiftIcon,
  image: ImageIcon,
  "info-circle": InfoCircleIcon,
  "info-fill": InfoCircleIcon,
  "insert-row-top": ArrowUpIcon,
  installation: InstallIcon,
  jump: JumpIcon,
  keyboard: KeyboardIcon,
  label: BookmarkIcon,
  launch: AnchorIcon,
  layers: LayersIcon,
  layout: LayoutIcon,
  link: LinkIcon,
  list: BulletpointIcon,
  loading: LoadingIcon,
  location: LocationIcon,
  lock: LockOnIcon,
  "logo-alipay": WalletIcon,
  "logo-android": MobileIcon,
  "logo-apple": MobileIcon,
  "logo-chrome": BrowseIcon,
  "logo-codepen": CodeIcon,
  "logo-github": LogoGithubIcon,
  "logo-ie": BrowseIcon,
  "logo-instagram": ImageIcon,
  "logo-qq": NotificationIcon,
  "logo-twitter": NotificationIcon,
  "logo-wechat": NotificationIcon,
  "logo-whatsapp": NotificationIcon,
  "logo-youtube": PlayIcon,
  magic: GiftIcon,
  mail: MailIcon,
  menu: MenuIcon,
  message: NotificationIcon,
  min: MinusIcon,
  mobile: MobileIcon,
  money: MoneyIcon,
  monitor: ChartIcon,
  more: MenuIcon,
  move: MoveIcon,
  mp: MapIcon,
  music: SoundIcon,
  navigate: ArrowRightIcon,
  news: NotificationIcon,
  night: FogNightIcon,
  null: ClearIcon,
  offline: NotificationIcon,
  order: OrderAscendingIcon,
  goods: GiftIcon,
  catalog: FolderOpenIcon,
  setting: SettingIcon,
  organization: UsergroupIcon,
  overview: BrowseIcon,
  pad: FileIcon,
  "page-first": PageFirstIcon,
  "page-last": PageLastIcon,
  palette: TipsIcon,
  pause: PauseIcon,
  pdf: FileIcon,
  pin: BookmarkIcon,
  "play-circle": PlayIcon,
  play: PlayIcon,
  plus: PlusIcon,
  power: PoweroffIcon,
  previous: PreviousIcon,
  print: PrintIcon,
  prompt: TipsIcon,
  qrcode: QrcodeIcon,
  "question-circle": HelpIcon,
  redux: CodeIcon,
  refresh: RefreshIcon,
  reload: RefreshIcon,
  ridicule: GiftIcon,
  rollback: PreviousIcon,
  "root-list": FileIcon,
  "round-arrow-down": ArrowDownIcon,
  "round-arrow-left": ArrowLeftIcon,
  "round-arrow-right": ArrowRightIcon,
  "round-arrow-up": ArrowUpIcon,
  screenshot: ScreenshotIcon,
  search: SearchIcon,
  secure: LockOnIcon,
  send: SendIcon,
  server: ServerIcon,
  "server-circle": ServerIcon,
  service: ServiceIcon,
  setup: SettingIcon,
  share: ShareIcon,
  shop: ShopIcon,
  "shopping-cart": GiftIcon,
  slideshow: PlayIcon,
  sound: SoundIcon,
  "star-fill": StarFilledIcon,
  star: StarFilledIcon,
  stop: StopIcon,
  swap: SwapIcon,
  task: TaskIcon,
  time: TimeIcon,
  timer: TimeIcon,
  tips: TipsIcon,
  tools: ToolsIcon,
  "trash-can": DeleteIcon,
  unbind: LinkIcon,
  unlock: LockOnIcon,
  upload: UploadIcon,
  usb: UsbIcon,
  "user-add": UserAddIcon,
  "user-blocked": UserBlockedIcon,
  "user-check": UserCheckedIcon,
  "user-delete": DeleteIcon,
  "user-group": UsergroupIcon,
  "user-search": UserSearchIcon,
  "user-talk": UserTalkIcon,
  video: VideoIcon,
  "video-camera": VideoCameraIcon,
  "view-list": ViewListIcon,
  voice: SoundIcon,
  wallet: WalletIcon,
  "warning-circle": NotificationIcon,
  wifi: WifiIcon,
  "zoom-in": ZoomInIcon,
  "zoom-out": ZoomOutIcon,
};

export function getIconComponent(iconName: string | undefined): Component | null {
  if (!iconName) return null;
  const name = iconName.toLowerCase() as IconName;
  return iconMap[name] || null;
}
