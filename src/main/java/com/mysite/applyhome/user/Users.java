package com.mysite.applyhome.user;

import com.mysite.applyhome.personalProfiles.PersonalProfiles;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "Users", uniqueConstraints = {
		@UniqueConstraint(columnNames = "user_login_id"),
		@UniqueConstraint(columnNames = "resident_registration_number")
})
public class Users {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "user_id")
	private Integer userId;

	@Column(name = "user_login_id", nullable = false, length = 50)
	private String userLoginId;

	@Column(name = "user_password_hash", nullable = false, length = 255)
	private String userPasswordHash;

	@OneToOne
	@JoinColumn(
			name = "resident_registration_number",
			referencedColumnName = "resident_registration_number",
			nullable = false,
			unique = true,
			foreignKey = @ForeignKey(name = "FK_Personal_Profiles_TO_Users")
	)
	private PersonalProfiles personalProfiles;
}
