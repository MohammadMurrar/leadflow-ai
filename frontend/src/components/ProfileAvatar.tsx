import { getInitials } from '../utils/getInitials'

export default function ProfileAvatar({ name }: { name: string }) {
  return <span className="profile-avatar" aria-hidden="true">{getInitials(name)}</span>
}
